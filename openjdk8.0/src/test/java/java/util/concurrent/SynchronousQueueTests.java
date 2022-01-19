package java.util.concurrent;

import org.junit.Test;

/**
 * @author Williami
 * @description
 * @date 2021/12/13
 */
public class SynchronousQueueTests {


    @Test
    public void testThreadPoolWithSyncQueue() {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(10, 15, 60L, TimeUnit.SECONDS, new SynchronousQueue());
        for (int i = 0; i < 20; i++) {
            int j = i;
            threadPoolExecutor.execute(() -> {
                int sleepInMills = ThreadLocalRandom.current().nextInt(500);
                try {
                    TimeUnit.MILLISECONDS.sleep(sleepInMills);
                    System.out.println(Thread.currentThread().getName() + " -- " + j);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    }

}
