package org.omg.PortableServer;


/**
* org/omg/PortableServer/AdapterActivator.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from c:/ojdkbuild/lookaside/java-1.8.0-openjdk/corba/src/share/classes/org/omg/PortableServer/poa.idl
* Saturday, October 20, 2018 4:19:43 PM PDT
*/


/**
	 * An adapter activator supplies a POA with the ability 
	 * to create child POAs on demand, as a side-effect of 
	 * receiving a request that names the child POA 
	 * (or one of its children), or when find_POA is called 
	 * with an activate parameter value of TRUE.
	 */
public interface AdapterActivator extends AdapterActivatorOperations, org.omg.CORBA.Object, org.omg.CORBA.portable.IDLEntity 
{
} // interface AdapterActivator
