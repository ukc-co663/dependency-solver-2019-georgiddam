package depsolver;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Main {

	private static final Pattern dependancyConflictPattern = Pattern.compile("([A-Z])(?:([<>]?=?)(\\d+(?:\\.\\d+)*))?");
	
	public static void main(String[] args) throws IOException {
		String repo = readFile(args[0]);
		String initial = readFile(args[1]);
		String constraint = readFile(args[2]);
		JsonElement jsonParsed = parseJson(repo, initial, constraint);
		createPackages(jsonParsed);
	}
	
	private static JsonElement parseJson(String repo, String initial, String constraint) throws FileNotFoundException {
		
		JsonParser parser = new JsonParser();
//		InputStream inputStream = Main.class.getClassLoader().getResourceAsStream("../example-0/repository.json");
//		System.out.println("What repo is this giving me \n" + repo);
//		File file = new File(repo);
//		System.out.println("What file is this giving me \n" + file);
//		InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(r);
//		System.out.println(inputStream);
//		Reader reader = new InputStreamReader(inputStream);
		JsonElement rootElement = parser.parse(repo);
		
		return rootElement;
	}
//	Add dependencies and constraints
	private static void addDependAndConflict(String[] dependants, Package currentJson, List<Package> allTasks, boolean isDep, int pos) { 
		
		List<Package> combinedDependants = new ArrayList<>();
		String name;
		String version = "0.0";
		String symbol = "=";
		Package newPackage;
		
		for (int k = 0; k < dependants.length; k++) {
			String depends = dependants[k];
			
			Matcher m = dependancyConflictPattern.matcher(depends);

			if(m.matches()) {
				name = m.group(1);
				symbol = m.group(2);
//				If there is no version, just equate for = 0.0
				if (!(m.group(3) == null)) {
					version = m.group(3);
				}
			
				for (int i = 0; i < allTasks.size(); i++) {
					
//					We can get a case where we have package of 3.2 and then a dependency of package <3.2 so we need to remove those who are exactly the same version only
					if(allTasks.get(i).name.equals(name) && allTasks.get(i).version.equals(version) && symbol.equals("=")) {
						
						System.out.println( "Skip package " + name + version +  symbol);
						allTasks.remove(i);
					} 
				}
				
				
				newPackage = new Package(name, version, symbol);		
				allTasks.add(newPackage);
				combinedDependants.add(newPackage);
			
			}
			
			for (int i = 0; i < allTasks.size(); i++) {
//				Adding Dependency or Conflict, final symbol is to check that the current symbol is not the same as the one we are trying to add as conflict or dependency
				if(allTasks.get(i).name.equals(currentJson.name) && allTasks.get(i).version.equals(currentJson.version) && !(allTasks.get(i).symbol.equals(symbol))) {
//					System.out.println();
//					If its a dependent add as a dependent
					if(isDep) {
						allTasks.get(i).addDependants(pos, combinedDependants);
//					Else add as a conflict
					} else {
						
						allTasks.get(i).addConflict(combinedDependants.get(0));
					}
				}
			}
		
				
		}

	}
	
	private static void createPackages(JsonElement json) {
		List<Package> allTasks = new ArrayList<>();
		Gson gson = new Gson();
		Package[] finalPackage = gson.fromJson(json, Package[].class);

//		Add all packages to a list, including dependents
		for (Package e: finalPackage) {
			allTasks.add(e);
			e.init();
        }

//		Using finalPackage here because dependencies are still in there

		for (Package e: finalPackage) {
		
//			Dependents
//			System.out.println(Arrays.deepToString(e.depends));
			if (Arrays.deepToString(e.depends) != "null") {
	        	for (int i = 0; i < e.depends.length; i++) {
	        		String[] dependants = new String[e.depends[i].length];
					for (int j = 0; j < e.depends[i].length; j++) {	
						dependants[j] = e.depends[i][j];
					}	
					addDependAndConflict(dependants, e, allTasks, true, i);
	        	}
			}
			
//			Constraints
			if (Arrays.deepToString(e.conflicts) != "null") {
				for (int i = 0; i < e.conflicts.length; i++) {
					String[] conflicts = {e.conflicts[i]};
					addDependAndConflict(conflicts, e, allTasks, false, i);
				}
			}
			
        }    
		
		
		StringBuilder results = new StringBuilder();
		
		for (int i = 0; i < allTasks.size(); i++) {
			Package task = allTasks.get(i);
			if(task.name.equals("A")) {
				task.run(results);
			}
		}
		
		System.out.println(results);
		
//		for (int i = 0; i < allTasks.size(); i++) {
//
//			System.out.println(allTasks.get(i));
//			
//		}
//		
		
	}
	
	  static String readFile(String filename) throws IOException {
		  	System.out.println("File Name Parsed is " + filename);
		    BufferedReader br = new BufferedReader(new FileReader(filename));
		    StringBuilder sb = new StringBuilder();
		    br.lines().forEach(line -> sb.append(line));
		    return sb.toString();
	  }
}


