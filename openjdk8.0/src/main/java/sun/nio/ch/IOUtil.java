/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.nio.ch;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * File-descriptor based I/O utilities that are shared by NIO classes.
 */

public class IOUtil {

    /**
     * Max number of iovec structures that readv/writev supports
     */
    static final int IOV_MAX;

    private IOUtil() { }                // No instantiation

    static int write(FileDescriptor fd, ByteBuffer src, long position,
                     NativeDispatcher nd)
        throws IOException
    {
        // 如果是堆外内存则直接写
        if (src instanceof DirectBuffer)
            return writeFromNativeBuffer(fd, src, position, nd);

        // Substitute a native buffer
        int pos = src.position();
        int lim = src.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        // 创建一块堆外内存，并将数据赋值到堆外内存中去
        ByteBuffer bb = Util.getTemporaryDirectBuffer(rem);
        try {
            bb.put(src);
            bb.flip();
            // Do not update src until we see how many bytes were written
            src.position(pos);

            int n = writeFromNativeBuffer(fd, bb, position, nd);
            if (n > 0) {
                // now update src
                src.position(pos + n);
            }
            return n;
        } finally {
            Util.offerFirstTemporaryDirectBuffer(bb);
        }
    }

    private static int writeFromNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                             long position, NativeDispatcher nd)
        throws IOException
    {
        int pos = bb.position();
        int lim = bb.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        int written = 0;
        if (rem == 0)
            return 0;
        if (position != -1) {
            // pread, pwrite - read from or write to a file descriptor at a given offset
            // pwrite系统调用，需要传入buffer起始地址和buffer count作为参数
            // 系统调用要求我们使用连续的地址空间作为buffer
            // ssize_t pwrite(int fd, const void *buf, size_t count, off_t offset);
            // pwrite() writes up to count bytes from the buffer starting at buf to the file descriptor fd at offset offset. The file offset is not changed.
            // 从文件描述符fd偏移位置offset处最多写入来自buffer的rem个字节
            written = nd.pwrite(fd,
                                ((DirectBuffer)bb).address() + pos,
                                rem, position);
        } else {
            // https://www.zhihu.com/question/60892134/answer/182225677
            // write() writes up to count bytes from the buffer pointed buf to the file referred to by the file descriptor fd.
            // ssize_t write(int fd, const void *buf, size_t count);
            // 以address作为基准地址，最多写入rem个字节数据到文件中fd
            // written:成功写入文件的字节个数  On success, the number of bytes written is returned (zero indicates nothing was written). On error, -1 is returned, and errno is set appropriately.
            written = nd.write(fd, ((DirectBuffer)bb).address() + pos, rem);
        }
        if (written > 0)
            // 移动position位置
            bb.position(pos + written);
        return written;
    }

    static long write(FileDescriptor fd, ByteBuffer[] bufs, NativeDispatcher nd)
        throws IOException
    {
        return write(fd, bufs, 0, bufs.length, nd);
    }

    static long write(FileDescriptor fd, ByteBuffer[] bufs, int offset, int length,
                      NativeDispatcher nd)
        throws IOException
    {
        IOVecWrapper vec = IOVecWrapper.get(length);

        boolean completed = false;
        int iov_len = 0;
        try {

            // Iterate over buffers to populate native iovec array.
            int count = offset + length;
            int i = offset;
            while (i < count && iov_len < IOV_MAX) {
                ByteBuffer buf = bufs[i];
                int pos = buf.position();
                int lim = buf.limit();
                assert (pos <= lim);
                int rem = (pos <= lim ? lim - pos : 0);
                if (rem > 0) {
                    vec.setBuffer(iov_len, buf, pos, rem);

                    // allocate shadow buffer to ensure I/O is done with direct buffer
                    if (!(buf instanceof DirectBuffer)) {
                        ByteBuffer shadow = Util.getTemporaryDirectBuffer(rem);
                        shadow.put(buf);
                        shadow.flip();
                        vec.setShadow(iov_len, shadow);
                        buf.position(pos);  // temporarily restore position in user buffer
                        buf = shadow;
                        pos = shadow.position();
                    }

                    vec.putBase(iov_len, ((DirectBuffer)buf).address() + pos);
                    vec.putLen(iov_len, rem);
                    iov_len++;
                }
                i++;
            }
            if (iov_len == 0)
                return 0L;

            long bytesWritten = nd.writev(fd, vec.address, iov_len);

            // Notify the buffers how many bytes were taken
            long left = bytesWritten;
            for (int j=0; j<iov_len; j++) {
                if (left > 0) {
                    ByteBuffer buf = vec.getBuffer(j);
                    int pos = vec.getPosition(j);
                    int rem = vec.getRemaining(j);
                    int n = (left > rem) ? rem : (int)left;
                    buf.position(pos + n);
                    left -= n;
                }
                // return shadow buffers to buffer pool
                ByteBuffer shadow = vec.getShadow(j);
                if (shadow != null)
                    Util.offerLastTemporaryDirectBuffer(shadow);
                vec.clearRefs(j);
            }

            completed = true;
            return bytesWritten;

        } finally {
            // if an error occurred then clear refs to buffers and return any shadow
            // buffers to cache
            if (!completed) {
                for (int j=0; j<iov_len; j++) {
                    ByteBuffer shadow = vec.getShadow(j);
                    if (shadow != null)
                        Util.offerLastTemporaryDirectBuffer(shadow);
                    vec.clearRefs(j);
                }
            }
        }
    }

    static int read(FileDescriptor fd, ByteBuffer dst, long position,
                    NativeDispatcher nd)
        throws IOException
    {
        if (dst.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");
        if (dst instanceof DirectBuffer)
            return readIntoNativeBuffer(fd, dst, position, nd);

        // Substitute a native buffer
        ByteBuffer bb = Util.getTemporaryDirectBuffer(dst.remaining());
        try {
            int n = readIntoNativeBuffer(fd, bb, position, nd);
            bb.flip();
            if (n > 0)
                dst.put(bb);
            return n;
        } finally {
            Util.offerFirstTemporaryDirectBuffer(bb);
        }
    }

    private static int readIntoNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                            long position, NativeDispatcher nd)
        throws IOException
    {
        int pos = bb.position();
        int lim = bb.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        if (rem == 0)
            return 0;
        int n = 0;
        if (position != -1) {
            n = nd.pread(fd, ((DirectBuffer)bb).address() + pos,
                         rem, position);
        } else {
            n = nd.read(fd, ((DirectBuffer)bb).address() + pos, rem);
        }
        if (n > 0)
            bb.position(pos + n);
        return n;
    }

    static long read(FileDescriptor fd, ByteBuffer[] bufs, NativeDispatcher nd)
        throws IOException
    {
        return read(fd, bufs, 0, bufs.length, nd);
    }

    static long read(FileDescriptor fd, ByteBuffer[] bufs, int offset, int length,
                     NativeDispatcher nd)
        throws IOException
    {
        IOVecWrapper vec = IOVecWrapper.get(length);

        boolean completed = false;
        int iov_len = 0;
        try {

            // Iterate over buffers to populate native iovec array.
            int count = offset + length;
            int i = offset;
            while (i < count && iov_len < IOV_MAX) {
                ByteBuffer buf = bufs[i];
                if (buf.isReadOnly())
                    throw new IllegalArgumentException("Read-only buffer");
                int pos = buf.position();
                int lim = buf.limit();
                assert (pos <= lim);
                int rem = (pos <= lim ? lim - pos : 0);

                if (rem > 0) {
                    vec.setBuffer(iov_len, buf, pos, rem);

                    // allocate shadow buffer to ensure I/O is done with direct buffer
                    if (!(buf instanceof DirectBuffer)) {
                        ByteBuffer shadow = Util.getTemporaryDirectBuffer(rem);
                        vec.setShadow(iov_len, shadow);
                        buf = shadow;
                        pos = shadow.position();
                    }

                    vec.putBase(iov_len, ((DirectBuffer)buf).address() + pos);
                    vec.putLen(iov_len, rem);
                    iov_len++;
                }
                i++;
            }
            if (iov_len == 0)
                return 0L;

            long bytesRead = nd.readv(fd, vec.address, iov_len);

            // Notify the buffers how many bytes were read
            long left = bytesRead;
            for (int j=0; j<iov_len; j++) {
                ByteBuffer shadow = vec.getShadow(j);
                if (left > 0) {
                    ByteBuffer buf = vec.getBuffer(j);
                    int rem = vec.getRemaining(j);
                    int n = (left > rem) ? rem : (int)left;
                    if (shadow == null) {
                        int pos = vec.getPosition(j);
                        buf.position(pos + n);
                    } else {
                        shadow.limit(shadow.position() + n);
                        buf.put(shadow);
                    }
                    left -= n;
                }
                if (shadow != null)
                    Util.offerLastTemporaryDirectBuffer(shadow);
                vec.clearRefs(j);
            }

            completed = true;
            return bytesRead;

        } finally {
            // if an error occurred then clear refs to buffers and return any shadow
            // buffers to cache
            if (!completed) {
                for (int j=0; j<iov_len; j++) {
                    ByteBuffer shadow = vec.getShadow(j);
                    if (shadow != null)
                        Util.offerLastTemporaryDirectBuffer(shadow);
                    vec.clearRefs(j);
                }
            }
        }
    }

    public static FileDescriptor newFD(int i) {
        FileDescriptor fd = new FileDescriptor();
        setfdVal(fd, i);
        return fd;
    }

    static native boolean randomBytes(byte[] someBytes);

    /**
     * Returns two file descriptors for a pipe encoded in a long.
     * The read end of the pipe is returned in the high 32 bits,
     * while the write end is returned in the low 32 bits.
     */
    static native long makePipe(boolean blocking);

    static native boolean drain(int fd) throws IOException;

    public static native void configureBlocking(FileDescriptor fd,
                                                boolean blocking)
        throws IOException;

    public static native int fdVal(FileDescriptor fd);

    static native void setfdVal(FileDescriptor fd, int value);

    static native int fdLimit();

    static native int iovMax();

    static native void initIDs();

    /**
     * Used to trigger loading of native libraries
     */
    public static void load() { }

    static {
        java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<Void>() {
                    public Void run() {
                        System.loadLibrary("net");
                        System.loadLibrary("nio");
                        return null;
                    }
                });

        initIDs();

        IOV_MAX = iovMax();
    }

}
