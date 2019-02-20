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
		List<Package> allTasks = createPackages(jsonParsed);
		startRun(allTasks, constraint);
	}
	
	  static String readFile(String filename) throws IOException {
		  	System.out.println("File Name Parsed is " + filename);
		    BufferedReader br = new BufferedReader(new FileReader(filename));
		    StringBuilder sb = new StringBuilder();
		    br.lines().forEach(line -> sb.append(line));
		    return sb.toString();
	  }
	
	private static JsonElement parseJson(String repo, String initial, String constraint) throws FileNotFoundException {
		JsonParser parser = new JsonParser();
		JsonElement rootElement = parser.parse(repo);
		
		return rootElement;
	}
//	Add dependencies and constraints
	private static void addDependAndConflict(String[] dependants, Package currentJson, List<Package> allTasks, boolean isDep, int pos) { 
		
		List<Package> combinedDependants = new ArrayList<>();
		String name;
		String version = "0.0";
		String symbol = "=";
//		create semantic version
		SemanticVersion semVersion = new SemanticVersion(version);
		for (int i = 0; i < dependants.length; i++) {
			String depends = dependants[i];
			
			Matcher m = dependancyConflictPattern.matcher(depends);
			if(m.matches()) {
				name = m.group(1);
//				If there is a symbol, add it
				if (!(m.group(2) == null)) {
					symbol = m.group(2);
				}
//				If there is a version, make it semantic
				if (!(m.group(3) == null)) {
					semVersion = new SemanticVersion(m.group(3));
				}
				
				
/*				
				Start looping through all tasks, check if the name of constraint/dependency 
				matches to that of inside the list.
				Once it does, look at its symbol and based on that, use the semVersion class
				to compare and based on results, add to the dependency list
*/
				for (int j = 0; j < allTasks.size(); j++) {
					Package tempTask = allTasks.get(j);
					if(tempTask.name.equals(name)) {
						if(symbol.equals(">=")) {
							int result = tempTask.semVersion.compareTo(semVersion);
							if(result == 0 || result == 1) {
								combinedDependants.add(tempTask);
							}
						}
						else if(symbol.equals("=")) {
							combinedDependants.add(tempTask);
						}
						else if(symbol.equals(">")) {
							int result = tempTask.semVersion.compareTo(semVersion);
							if(result == 1) {
								combinedDependants.add(tempTask);
							}
						}
						else if(symbol.equals("<")) {
							int result = tempTask.semVersion.compareTo(semVersion);
							if(result == -1) {
								combinedDependants.add(tempTask);
							}
						}
						else if(symbol.equals("<=")) {
							int result = tempTask.semVersion.compareTo(semVersion);
							if(result == -1 || result == 0) {
								combinedDependants.add(tempTask);
							}
						} else {
							System.out.println("Should never get here, what is my symbol " + symbol);
						}
					}
				}
			}
			
//			If its a dependency
			if(isDep) {
				currentJson.addDependants(pos, combinedDependants);
//			Else add as a conflict
			} else {
				for (int j = 0; j < combinedDependants.size(); j++) {
					currentJson.addConflict(combinedDependants.get(j));
				}
			}
		
		}

	}
	
	private static List<Package> createPackages(JsonElement json) {
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
		
		

		
		
		for (int i = 0; i < allTasks.size(); i++) {
//			Shows me the Linked Dependencies in the end
			System.out.println(allTasks.get(i));
			
		}
//		
		return allTasks;
		
	}
	
	public static void startRun(List<Package> allTasks, String constraint) {
//		Temp run result
//		TODO need to change this to use the array
		StringBuilder results = new StringBuilder();
		for (int i = 0; i < allTasks.size(); i++) {
			Package task = allTasks.get(i);
			if(task.name.equals("A")) {
				task.run(results);
			}
		}
		System.out.println(results);
	}
}