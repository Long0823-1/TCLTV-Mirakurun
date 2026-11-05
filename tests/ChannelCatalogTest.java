package io.github.long0823.tclmirakurun;
import java.util.*;
public class ChannelCatalogTest {
 static int checks;
 static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 static String row(int onid,int tsid,int sid,String system){return "Row: 0 original_network_id="+onid+", transport_stream_id="+tsid+", service_id="+sid+", type=TYPE_ISDB_"+system+", service_type=EN_TCL_DTV, browsable=1, display_name=試験局\n";}
 public static void main(String[] args)throws Exception {
  List<ChannelCatalog.Channel> list=ChannelCatalog.parse(row(32738,32738,1040,"T")+row(4,16400,101,"S")+row(4,16400,102,"S")+row(6,24608,333,"S")+row(7,28704,55,"S")+row(10,1,1,"S"));
  check(list.size()==4);check(list.get(0).type.equals("GR"));check(list.get(1).type.equals("BS"));check(list.get(2).type.equals("CS"));check(list.get(3).type.equals("CS"));
  check(list.get(1).channel.equals("4_16400_101"));
  check(ChannelCatalog.parse(row(4,16400,101,"S")).size()==1);
  List<ChannelCatalog.Channel> cable=ChannelCatalog.parse(row(65534,48,200,"C")+row(4,16528,211,"C")+row(6,24608,333,"C")+row(32738,32738,1040,"T"));
  check(cable.size()==4);check(cable.get(0).type.equals("GR"));check(cable.get(1).type.equals("BS"));check(cable.get(2).type.equals("CS"));check(cable.get(3).type.equals("SKY"));
  List<ChannelCatalog.Channel> noSat=ChannelCatalog.parse(row(32738,32738,1040,"T")+row(4,16400,101,"S")+row(4,16528,211,"C"),false);
  check(noSat.size()==2);check(noSat.get(0).type.equals("GR"));check(noSat.get(1).channel.equals("4_16528_211"));
  for(String rows:new String[]{"No result found",row(4,0,101,"S"),row(4,16400,70000,"S"),row(4,16400,101,"S").replace("browsable=1","browsable=0"),row(4,16400,101,"S").replace("EN_TCL_DTV","EN_TCL_DATA")}){
   boolean failed=false;try{ChannelCatalog.parse(rows);}catch(java.io.IOException e){failed=true;}check(failed);
  }
  System.out.println("Mirakurun three-wave catalog checks: "+checks);
  if(args.length>0){
   List<ChannelCatalog.Channel> actual=ChannelCatalog.parse(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),java.nio.charset.StandardCharsets.UTF_8));
   for(String type:new String[]{"GR","BS","CS"})System.out.println("scanned "+type+" muxes: "+actual.stream().filter(c->c.type.equals(type)).count());
  }
 }
}
