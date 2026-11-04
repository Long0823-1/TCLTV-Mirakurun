package io.github.long0823.tclmirakurun;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Explicit user-triggered updates of the Mirakurun npm package, not the APK. */
final class CoreUpdate {
 static final String STAGE="core-update";
 static final class Info {
  final String current,previous,node,latest,integrity,license;
  final boolean online;
  Info(JSONObject installed,JSONObject npm)throws JSONException,IOException {
   current=installed.getString("current");previous=installed.isNull("previous")?null:installed.getString("previous");node=installed.getString("node");
   online=npm!=null;
   if(npm==null){latest=current;integrity="";license="Apache-2.0";return;}
   latest=npm.getString("version");integrity=npm.getJSONObject("dist").getString("integrity");license=npm.getString("license");
   if(!"mirakurun".equals(npm.getString("name"))||!"Apache-2.0".equals(license)||!latest.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!integrity.matches("sha512-[A-Za-z0-9+/]+=*"))throw new IOException("公式npmの更新情報を確認できません");
  }
  boolean newer(){return compare(latest,current)>0;}
  boolean supported(){return latest.startsWith("4.");}
  String summary(){return "インストール済み：Mirakurun "+current+"\n最新の安定版："+(online?latest:"取得できませんでした")+"\nNode.js："+node+"\n前の本体："+(previous==null?"バックアップなし":previous)+"\nライセンス："+license+"\n更新元：https://registry.npmjs.org/mirakurun\n変更点：https://github.com/Chinachu/Mirakurun/releases/tag/"+latest;}
 }
 static int compare(String a,String b){
  if(!a.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!b.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))return 0;
  String[] aa=a.split("\\."),bb=b.split("\\.");
  try{for(int i=0;i<3;i++){int c=Long.compare(Long.parseLong(aa[i]),Long.parseLong(bb[i]));if(c!=0)return c;}}catch(NumberFormatException ignored){}return 0;
 }
 private static JSONObject json(String url)throws Exception {
  HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
  connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setRequestProperty("Accept","application/json");
  try{if(connection.getResponseCode()!=200)throw new IOException("更新情報 HTTP "+connection.getResponseCode());
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>1024*1024)throw new IOException("更新情報が大きすぎます");bytes.write(b,0,n);}}
   return new JSONObject(bytes.toString("UTF-8"));
  }finally{connection.disconnect();}
 }
 private static File stage(Context context)throws Exception {
  File dir=new File(context.getFilesDir(),STAGE);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("更新用の保存先を作成できません");
  for(String name:new String[]{"update-core.sh","core-update.cjs","core-health.cjs","patch-runtime.cjs","crc32-fallback.cjs"}){
   try(InputStream in=context.getAssets().open(name);OutputStream out=new FileOutputStream(new File(dir,name))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  }
  ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
  LinkProperties link=manager.getLinkProperties(manager.getActiveNetwork());
  StringBuilder dns=new StringBuilder();
  if(link!=null)for(InetAddress server:link.getDnsServers())if(!server.isLoopbackAddress()&&!server.isAnyLocalAddress())dns.append("nameserver ").append(server.getHostAddress()).append('\n');
  if(dns.length()>0)try(OutputStream out=new FileOutputStream(new File(dir,"resolv.conf"))){out.write(dns.toString().getBytes(StandardCharsets.US_ASCII));}
  else new File(dir,"resolv.conf").delete();
  return dir;
 }
 private static String command(File dir,String action)throws Exception {
  String operation="sh "+Root.quote(new File(dir,"update-core.sh").getAbsolutePath())+" "+Root.quote(dir.getAbsolutePath())+" "+action;
  if(action.equals("versions"))return Root.capture(operation);
  return Root.capture(operation+" >"+Installer.BASE+"/core-update.log 2>&1; result=$?; tail -n 60 "+Installer.BASE+"/core-update.log; exit $result");
 }
 static Info check(Context context)throws Exception {
  File dir=stage(context);String raw=command(dir,"versions");
  JSONObject installed=null;
  for(String line:raw.split("\\r?\\n"))if(line.startsWith("{"))installed=new JSONObject(line);
  if(installed==null)throw new IOException("先にMirakurunをセットアップしてください");
  JSONObject npm;
  try{npm=json("https://registry.npmjs.org/mirakurun/latest");}catch(IOException error){npm=null;}
  return new Info(installed,npm);
 }
 static String install(Context context,Info info)throws Exception {
  if(!info.newer()||!info.supported())throw new IOException("この更新版は自動適用できません");
  File dir=stage(context);JSONObject request=new JSONObject();request.put("version",info.latest);request.put("integrity",info.integrity);
  try(OutputStream out=new FileOutputStream(new File(dir,"request.json"))){out.write(request.toString().getBytes(StandardCharsets.UTF_8));}
  try{return command(dir,"update");}finally{new File(dir,"request.json").delete();}
 }
 static String rollback(Context context)throws Exception {return command(stage(context),"rollback");}
 static String licenses()throws Exception {
  return Root.capture("cat "+Installer.BASE+"/rootfs/opt/mirakurun/node_modules/mirakurun/LICENSE; if [ -f "+Installer.BASE+"/rootfs/opt/mirakurun/tcl-dependency-licenses.json ]; then cat "+Installer.BASE+"/rootfs/opt/mirakurun/tcl-dependency-licenses.json; fi");
 }
}
