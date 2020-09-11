package com.sun.corba.se.spi.activation;


/**
* com/sun/corba/se/spi/activation/ServerOperations.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from c:/ojdkbuild/lookaside/java-1.8.0-openjdk/corba/src/share/classes/com/sun/corba/se/spi/activation/activation.idl
* Saturday, October 20, 2018 4:19:36 PM PDT
*/


/** Server callback API, passed to Activator in active method.
    */
public interface ServerOperations 
{

  /** Shutdown this server.  Returns after orb.shutdown() completes.
	*/
  void shutdown ();

  /** Install the server.  Returns after the install hook completes
	* execution in the server.
	*/
  void install ();

  /** Uninstall the server.  Returns after the uninstall hook
	* completes execution.
	*/
  void uninstall ();
} // interface ServerOperations
