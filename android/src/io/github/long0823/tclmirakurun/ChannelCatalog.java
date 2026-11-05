package io.github.long0823.tclmirakurun;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Public scanned muxes only. BS ONID=4; 110-degree CS ONID=6 or 7. */
final class ChannelCatalog {
 static final class Channel {
  final String name,type,channel;
  Channel(String n,String t,String c){name=n;type=t;channel=c;}
 }
 static List<Channel> parse(String rows)throws IOException {return parse(rows,true);}
 /** includeSatellite=false drops the TV database's own BS/CS entries (they share ONID/service IDs with the CATV re-broadcast). */
 static List<Channel> parse(String rows,boolean includeSatellite)throws IOException {
  Pattern pattern=Pattern.compile("original_network_id=(\\d+), transport_stream_id=(\\d+), service_id=(\\d+), type=(TYPE_ISDB_[TSC]), service_type=(?:EN_TCL_DTV|SERVICE_TYPE_AUDIO_VIDEO), browsable=1, display_name=(.+)");
  List<Channel> result=new ArrayList<>();Set<String> seen=new HashSet<>();
  for(String row:rows.split("\\r?\\n")){
   Matcher m=pattern.matcher(row);if(!m.find())continue;
   int[] ids=new int[3];
   for(int i=0;i<3;i++){try{ids[i]=Integer.parseInt(m.group(i+1));}catch(NumberFormatException e){throw new IOException("invalid channel IDs",e);}if(ids[i]<1||ids[i]>65535)throw new IOException("invalid channel IDs");}
   String type="GR";
   if(m.group(4).equals("TYPE_ISDB_C")){type=ids[0]==4?"BS":(ids[0]==6||ids[0]==7?"CS":"SKY");} // CATV経由のBS/CSはBS/CS、CATVの専門チャンネルはSKY
   else if(m.group(4).equals("TYPE_ISDB_S")){
    if(!includeSatellite)continue;
    if(ids[0]==4)type="BS";else if(ids[0]==6||ids[0]==7)type="CS";else continue;
   }
   String key=type+":"+ids[0]+"_"+ids[1];if(!seen.add(key))continue;
   String name=m.group(5).trim();if(name.isEmpty()||name.equals("NULL")||name.length()>200)throw new IOException("invalid channel name");
   result.add(new Channel(name,type,ids[0]+"_"+ids[1]+"_"+ids[2]));
   if(result.size()>512)throw new IOException("too many channels");
  }
  // TVTest (BonDriver_Mirakurun) creates one tuner space per run of the same type: keep each type together.
  final List<String> order=Arrays.asList("GR","BS","CS","SKY");
  Collections.sort(result,new Comparator<Channel>(){public int compare(Channel a,Channel b){return Integer.compare(order.indexOf(a.type),order.indexOf(b.type));}});
  if(result.isEmpty())throw new IOException("TV本体で先に地デジ・BS/CSのチャンネルスキャンを行ってください");
  return result;
 }
}
