package com.sun.corba.se.spi.activation;


/**
* com/sun/corba/se/spi/activation/ORBAlreadyRegistered.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from c:/ojdkbuild/lookaside/java-1.8.0-openjdk/corba/src/share/classes/com/sun/corba/se/spi/activation/activation.idl
* Saturday, October 20, 2018 4:19:36 PM PDT
*/

public final class ORBAlreadyRegistered extends org.omg.CORBA.UserException
{
  public String orbId = null;

  public ORBAlreadyRegistered ()
  {
    super(ORBAlreadyRegisteredHelper.id());
  } // ctor

  public ORBAlreadyRegistered (String _orbId)
  {
    super(ORBAlreadyRegisteredHelper.id());
    orbId = _orbId;
  } // ctor


  public ORBAlreadyRegistered (String $reason, String _orbId)
  {
    super(ORBAlreadyRegisteredHelper.id() + "  " + $reason);
    orbId = _orbId;
  } // ctor

} // class ORBAlreadyRegistered
