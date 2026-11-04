package io.github.long0823.tclmirakurun;
public final class CoreUpdateVersionTest {
 public static void main(String[] args){
  String[][] cases={{"4.2.1","4.1.5","1"},{"4.2.1","4.2.1","0"},{"4.2.1","4.2.2","-1"},{"4.10.0","4.9.9","1"},{"5.0.0","4.99.99","1"},{"4.2.1-beta.1","4.1.5","0"},{"4.2.1;reboot","4.1.5","0"},{"4.999999999999999999999.0","4.1.5","0"}};
  for(String[] c:cases)if(Integer.signum(CoreUpdate.compare(c[0],c[1]))!=Integer.parseInt(c[2]))throw new AssertionError(c[0]);
  System.out.println("Core update version checks: "+cases.length);
 }
}
