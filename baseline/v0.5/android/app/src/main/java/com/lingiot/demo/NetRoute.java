package com.lingiot.demo;
import android.content.Context;
import android.net.*;
import java.net.InetAddress;
// Select per-request routing without changing the whole app's default network.
public final class NetRoute {
  public static Network internet(Context c){ConnectivityManager m=c.getSystemService(ConnectivityManager.class);if(m==null)return null;Network active=m.getActiveNetwork();if(valid(m,active))return active;for(Network n:m.getAllNetworks())if(valid(m,n))return n;return null;}
  private static boolean valid(ConnectivityManager m,Network n){if(n==null)return false;NetworkCapabilities p=m.getNetworkCapabilities(n);return p!=null&&p.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&p.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}
  public static Network local(Context c,String ip){try{ConnectivityManager m=c.getSystemService(ConnectivityManager.class);if(m==null)return null;InetAddress target=InetAddress.getByName(ip);Network best=null;int prefix=0;for(Network n:m.getAllNetworks()){NetworkCapabilities caps=m.getNetworkCapabilities(n);LinkProperties lp=m.getLinkProperties(n);if(caps==null||lp==null||!(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))continue;for(RouteInfo r:lp.getRoutes())if(r.getDestination().getPrefixLength()>prefix&&r.matches(target)){prefix=r.getDestination().getPrefixLength();best=n;}}return best;}catch(Exception e){return null;}}
}
