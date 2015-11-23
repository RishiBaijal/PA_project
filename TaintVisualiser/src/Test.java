import java.io.BufferedReader;
import java.io.InputStreamReader;


public class Test {
	public static void main(String args[])throws Exception
	{
		BufferedReader obj = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Enter a string");
		String s = obj.readLine();
		String x=s;
		System.out.println(s);
		System.out.println(x);
	}

}
