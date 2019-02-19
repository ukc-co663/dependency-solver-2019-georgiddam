package depsolver;
import java.util.Arrays;

public class SemanticVersion implements Comparable<SemanticVersion> {
	
	String semanticVersion;
	
	int[] convertedVersion;
	
	String[] splitVersion;
	
	public SemanticVersion(String semanticVersion) {
		
		this.semanticVersion = semanticVersion;
		splitVersion = semanticVersion.split("\\.");
		convertedVersion = new int[splitVersion.length];
		
		for(int i = 0; i<convertedVersion.length; i++) {
			convertedVersion[i] = Integer.valueOf(splitVersion[i]);
		}
	}
	
	public int compareTo(SemanticVersion anotherVer) {
		int size = Math.min(convertedVersion.length, anotherVer.convertedVersion.length);
		
		int i = 0;
		for (; i < size; i++) {
			int result = Integer.compare(convertedVersion[i], anotherVer.convertedVersion[i]);
			if (result != 0) {
				return result;
			}
		}
		
		if (convertedVersion.length > i ) {
			return 1;
		} else if (anotherVer.convertedVersion.length > i) {
			return -1;
		}
		
		return 0;
	}
	
	public String toString() {
		return  "SemV " + Arrays.toString(convertedVersion);
	}
}
