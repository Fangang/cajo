package gnu.cajo.utils;

import gnu.cajo.invoke.*;
import java.io.*;
import java.rmi.registry.*;
import java.text.DateFormat;
import java.rmi.RemoteException;
import java.rmi.MarshalledObject;
import java.util.zip.GZIPOutputStream;

/*
 * Standard Item Server Utility
 *
 * For issues or suggestions mailto:cajo@dev.java.net
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, at version 2.1 of the license, or any
 * later version.  The license differs from the GNU General Public License
 * (GPL) to allow this library to be used in proprietary applications. The
 * standard GPL would forbid this.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * To receive a copy of the GNU General Public License visit their website at
 * http://www.gnu.org or via snail mail at Free Software Foundation Inc.,
 * 59 Temple Place Suite 330, Boston MA 02111-1307 USA
 */
/**
 * These routines are used for server item construction.  The items can be
 * utilized by remote clients to compose larger, cooperative, functionality.
 * It creates an rmiregistry automatically, upon class loading, since any given
 * VM instance can only have one.  It will be used to bind all item servers for
 * external access.  A given application can bind as many items as it wants.
 * <p>A security policy file, named "server.policy" will be loaded from the
 * local directory. By default, the policy file need only allow the following
 * permissions:
 * <p><pre> grant codeBase "file:${java.class.path}" {
 *    permission java.security.AllPermission;
 * };
 * grant {
 *    permission java.net.SocketPermission "*:1024-", "accept";
 *    permission java.net.SocketPermission "*", "connect";
 * };</pre><p>
 * This will allow the three things:<ul>
 * <li> The server codebase has full priviliges.
 * <li> Proxies can only create server sockets only on non-priviliged local ports.
 * <li> Proxies can only create client sockets to any remote port, on any host.
 * </ul><i>Note:</i> if  the {@link gnu.cajo.invoke.Remote Remote} class needs
 * configuration, it must be done <b>before</b> binding an item, since doing
 * this will use the server's assigned name and port number. Configuration is
 * accomplished by calling its {@link gnu.cajo.invoke.Remote#config config}
 * static method.
 * 
 * @version 1.0, 01-Nov-99 Initial release
 * @author John Catherino
 */
public class ItemServer {
   static {
      System.setProperty("java.rmi.server.disableHttp", "true");
      if (System.getProperty("java.security.policy") == null)
         System.setProperty("java.security.policy", "server.policy");
   }
   private static Invoke main;
   /**
    * The reference to the sole local rmiregistry of this VM. All binding
    * operations must make use of this instance, as there can be only one
    * rmiregistry per session.  All other items bound on this registry will
    * also have to share the same port.  The port used for the registry will
    * be the same one used to communicate with all local server instances.
    * Generally this instance is manipulated solely by the object. However,
    * it is made public, to allow the application options; such as dynamically
    * unbinding items, or binding objects of other types.
    */
   public static Registry registry;
   /**
    * Nothing happens in the constructor of this class, as all of its
    * methods are static, and it has no instance variables.  This class exists
    * solely as a server item creation facilitator.
    */
   public ItemServer() {}
   /**
    * This method enables this VM to host proxies, and accept other mobile code,
    * from other remote servers. Hosting mobile code can result in the
    * overloading of this server VM, either accidentially, or maliciously.
    * Therefore hosting should be done either in a trusted environment, or on
    * a non-essential VM. Hosting of mobile code is disabled by default.
    * <p><i>Note:</i> accepting proxies may be disabled via a command line
    * argument at the server's startup, in which case, this will accomplish
    * nothing.  The loading of proxies can be prohibited when launching the
    * server with the <b>-Djava.security.manager</b> switch. It installs a
    * default SecurityManager, which will not allow the loading of proxies, or
    * any other type of mobile code, and prohibits itself from being replaced
    * by the application. <i>Note:</i> this is an <i>extremely</i> important
    * command line switch; worth <u>memorizing</u>!
    * @throws SecurityException If a SecurityManager is already installed, and
    * explicitly prohibits itself from being replaced.
    */
   public static void acceptProxies() throws SecurityException {
      System.setSecurityManager(new java.rmi.RMISecurityManager());
   }
   /**
    * This method remotes the provided item in the local rmiregistry. The
    * registry will not be created until the binding of the first item, to
    * allow the opportunity for the {@link gnu.cajo.invoke.Remote Remote}
    * class' network settings to be {@link gnu.cajo.invoke.Remote#config
    * configured}. Strictly speaking, it performs a rebind operation on the
    * rmiregistry, to more easily allow the application to dynamically replace
    * server items at runtime, if necessary. Since the registry is not shared
    * with other applications, checking for already bound items is unnecessary.
    * <p> If the provided item implements the {@link gnu.cajo.invoke.Invoke
    * Invoke} interface; it will first be invoked with the method name
    * "startThread", and a null argument, to signal it to start its main
    * processing thread. Next it will be invoked with a "setProxy", and a
    * remote reference to itself, with which it can share with remote VMs, in
    * an application specific manner.
    * @param item The item to be bound.  It may be either local to the machine,
    * or remote, it can even be a proxy from a remote item, if proxy
    * {@link #acceptProxies acceptance} was enabled for this VM.
    * @param name The name under which to bind the item reference in the
    * local rmiregistry.
    * @return A remoted reference to the item within the context of this VM's
    * settings.
    * @throws RemoteException If the registry could not be created.
    */
   public static Remote bind(Object item, String name) throws RemoteException {
      if (registry == null) {
         registry = LocateRegistry.
            createRegistry(Remote.getServerPort(), Remote.rcsf, Remote.rssf);
      }
      Remote handle = item instanceof Remote ? (Remote)item : new Remote(item);
      if (item instanceof Invoke) try {
         ((Invoke)item).invoke("startThread", null);
         ((Invoke)item).invoke("setProxy", new MarshalledObject(handle));
      } catch(Exception x) {}
      registry.rebind(name, handle);
      return handle;
   }
   /**
    * This method is used to bind a proxy serving item. It will remote a
    * reference to the server item, and bind in it the local rmiregistry under
    * the name provided. It works identically to the bind operation for regular
    * server items, with a few additional steps.<p>
    * If the proxy implements {@link gnu.cajo.invoke.Invoke Invoke}, it will
    * be called with a remote reference to the serving item, with a method
    * argument of "setProxy". If the item implements Invoke it will be called
    * with a method string of "setProxy" and a {@link java.rmi.MarshalledObject
    * MarshalledObject} containing the proxy item.
    * @param item The item to be bound.  It may be either local to the machine,
    * or remote, it can even be a proxy from a remote item, if proxy
    * {@link #acceptProxies acceptance} was enabled for this VM.
    * @param name The name under which to bind the item reference in the
    * local rmiregistry.
    * @param proxy The proxy item to be sent to requesting clients.
    * @return A remoted reference to the item within the context of this VM's
    * settings.
    * @throws RemoteException If the registry could not be created.
    */
   public static Remote bind(Object item, String name, Object proxy)
      throws RemoteException {
      if (registry == null) {
         registry = LocateRegistry.
            createRegistry(Remote.getServerPort(), Remote.rcsf, Remote.rssf);
      }
      Remote handle = item instanceof Remote ? (Remote)item : new Remote(item);
      if (proxy instanceof Invoke) try {
         ((Invoke)proxy).invoke("setItem", handle);
      } catch(Exception x) {}
      if (item instanceof Invoke) try {
         ((Invoke)item).invoke("startThread", null);
         ((Invoke)item).invoke("setProxy", new MarshalledObject(proxy));
      } catch(Exception x) {}
      registry.rebind(name, handle);
      return handle;
   }
   /**
    * The application loads either a zipped marshalled object (zedmob) from a
    * URL, a file, or alternately, it will fetch a remote item reference from
    * an rmiregistry. It uses the {@link gnu.cajo.invoke.Remote#getItem getitem}
    * method of the {@link gnu.cajo.invoke.Remote Remote} class. The
    * application startup will be announced over a default
    * {@link Multicast Multicast} object.  The remote interface to the object
    * will be bound under the name "main" in the local rmiregistry using the
    * local {@link #bind bind} method.
    * <p>The startup can take up to six optional configuration parameters,
    * which must be in order, progressing from most important, to most
    * least:<ul>
    * <li> args[0] The URL where to get the object: file:// http:// ftp://
    * /path/file, path/file or alternatively; //[host][:port]/[name] -- the
    * host port and name are optional, if omitted the host is presumed local,
    * the port 1099, and the name proxy. If no arguments are provided, this
    * will be assumed to be ///main.
    * <li> args[1] The optional external client host name, if using NAT.
    * <li> args[2] The optional external client port number, if using NAT.
    * <li> args[3] The optional internal client host name, if multi home/NIC.
    * <li> args[4] The optional internal client port number, if using NAT.
    * <li> args[5] The optional URL where to get a proxy item: file://
    * http:// ftp:// ..., //host:port/name (rmiregistry), /path/name
    * (serialized), or path/name (class).  It will be passed into the loaded
    * item as the sole argument to its setItem method.<ul>
    */
   public static void main(String args[]) {
      try {
         String url        = args.length > 0 ? args[0] : null;
         String clientHost = args.length > 1 ? args[1] : null;
         int clientPort    = args.length > 2 ? Integer.parseInt(args[2]) : 0;
         String localHost  = args.length > 3 ? args[3] : null;
         int localPort     = args.length > 4 ? Integer.parseInt(args[4]) : 0;
         Remote.config(localHost, localPort, clientHost, clientPort);
         main = Remote.getItem(url);
         if (args.length > 5) main.invoke("setItem", Remote.getItem(args[5]));
         main = bind(main, "main");
         new Multicast().announce((Remote)main, 16);
         acceptProxies();
         System.out.println("Server started: " +
            DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL).
               format(new java.util.Date()));
         System.out.print("Serving item ");
         System.out.print(args[0]);
         System.out.println(" bound under name item");
         System.out.print("locally  operating on ");
         System.out.print(Remote.getServerHost());
         System.out.print(" port ");
         System.out.println(Remote.getServerPort());
         System.out.print("remotely operating on ");
         System.out.print(Remote.getClientHost());
         System.out.print(" port ");
         System.out.println(Remote.getClientPort());
      } catch (Exception x) { x.printStackTrace(System.err); }
   }
}
