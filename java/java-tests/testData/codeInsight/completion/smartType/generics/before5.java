class A{
 class Map<U,V>{
  V get(U u){}
 }

 {
   String str = new Map<Object, String>().g<caret>
 }
}