import java.util.HashSet;
import java.util.Set;

public class Main {

	public static void main(String[] args) {
		Set<Integer> S = new HashSet<>();
		
		S.add(1);
		S.add(2);
		S.add(3);
		
		int sum=0;
		for(Integer s : S)
		{
			s=sum+1;
		}
		//System.out.println(sum);
		
		S.stream().forEach(s -> { s=sum+1; });
	}

}
