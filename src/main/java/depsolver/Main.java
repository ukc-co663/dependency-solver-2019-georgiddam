package depsolver;
import java.io.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.io.parsers.ParserException;
import org.logicng.io.parsers.PropositionalParser;
import org.logicng.solvers.MiniSat;
import org.logicng.solvers.SATSolver;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import depsolver.Package;

public class Main {

	private static final Comparator<Package> PackageVersionComparator = new Comparator<>() {
		
		@Override
		public int compare(Package arg0, Package arg1) {
			return arg1.version.compareTo(arg0.version);
		}
	};
	private static final Pattern dependancyConflictPattern = Pattern.compile("([\\w-.\\d+]+)(?:([<>]?=?)(\\d+(?:\\.\\d+)*))?");
	private static final Pattern toInstallPattern = Pattern.compile("\"([+-])?([\\w-]+)([<>]?=?)?(\\d+(?:\\.\\d+)*)?\"");
	private static final Pattern toInitialPattern = Pattern.compile("\"([\\w-]+)([<>]?=?)?(\\d+(?:\\.\\d+)*)?\"");
	
	private static List<Package> allTasks = new ArrayList<>(); 
	
	private static String solver = "";
	
	private static Map<String, SortedSet<Package>> tasksMap = new HashMap<>();
	
	private static int lowestSize;
	
	private static StringBuilder finalResult = new StringBuilder();
	
	private static List<Package> packagesToInstall;
	private static List<Package> packagesToUninstall;
	private static List<Package> mustBeUninstalled;
	
	public static void main(String[] args) throws IOException, ParserException {
		String repo = readFile(args[0]);
		String initial = readFile(args[1]);
		initial = initial.replaceAll("\\s+","");
		initial = initial.replaceAll("\\[","");
		initial = initial.replaceAll("\\]","");
		String[] initialArr = initial.split(",");

		String toInstall = readFile(args[2]);
		toInstall = toInstall.replaceAll("\\s*","");
		toInstall = toInstall.replaceAll("\\[","");
		toInstall = toInstall.replaceAll("\\]","");

		String[] toInstallArr;
		if (toInstall.contains(",")) {
			toInstallArr = toInstall.split(",");
		} else {
			toInstallArr =  new String[]{toInstall};
		}
		
		JsonElement jsonParsed = parseJson(repo);
		allTasks = createPackages(jsonParsed, initialArr);
		
		
	
		for (Package p: allTasks) {
			String pName = p.name;
			
			SortedSet<Package> temp = tasksMap.get(pName);
			if(temp == null) {
				temp = new TreeSet<>(PackageVersionComparator);
				tasksMap.put(pName, temp);
			}
			temp.add(p);							
		}
		startRun(tasksMap, toInstallArr);
//		System.out.println(compareStrings("1", "2"));
		System.out.println(finalResult);
		
	}
	

	
	private static String readFile(String filename) throws IOException {
		    BufferedReader br = new BufferedReader(new FileReader(filename));
		    StringBuilder sb = new StringBuilder();
		    br.lines().forEach(line -> sb.append(line));
		    return sb.toString();	
	 }
	
	private static JsonElement parseJson(String text) throws FileNotFoundException {
		JsonParser parser = new JsonParser();
		JsonElement rootElement = parser.parse(text);
		return rootElement;
	}
	
	private static int compareStrings(String x, String y) {

		List<String> x1 = Arrays.asList(x.split("\\."));
		List<String> y1 = Arrays.asList(y.split("\\."));

        int[] x2 = x1.stream().mapToInt(Integer::parseInt).toArray();
        int[] y2 = y1.stream().mapToInt(Integer::parseInt).toArray();
		
		int size = Math.min(x2.length, y2.length);
		int i = 0;
		for (; i < size; i++) {
			int result = Integer.compare(x2[i], y2[i]);
			if (result != 0) {
				return result;
			}
		}
		
		if (x2.length > i ) {
			return 1;
		} else if (y2.length > i) {
			return -1;
		}
		return 0;
	}
	
//	Add dependencies and constraints
	private static void addDependAndConflict(String[] dependants, Package currentJson, List<Package> allTasks, boolean isDep, int pos) { 
		List<Package> combinedDependants = new ArrayList<>();
        String name;
        String version = "1";
        String symbol = "=";
        for (int i = 0; i < dependants.length; i++) {
            String depends = dependants[i];
            Matcher m = dependancyConflictPattern.matcher(depends);
            if(m.matches()) {
                name = m.group(1);
                if (!(m.group(2) == null)) {
                    symbol = m.group(2);
                }
                if (!(m.group(3) == null)) {
                    version = (m.group(3));
                } 
                for (int j = 0; j < allTasks.size(); j++) {
                    Package tempTask = allTasks.get(j);
                    if(tempTask.name.equals(name)) {
                        int result = compareStrings(tempTask.version,version);
                        if(symbol.equals("=")) {
                            combinedDependants.add(tempTask);
                        }
                        else if(symbol.equals(">=")) {
                            if(result == 0 || result > 0) {
                                combinedDependants.add(tempTask);
                            }
                        }
                        else if(symbol.equals(">")) {
                            if(result > 0) {
                                combinedDependants.add(tempTask);
                            }
                        }
                        else if(symbol.equals("<")) {
                            if(result < 0) {
                                combinedDependants.add(tempTask);
                            }
                        }
                        else if(symbol.equals("<=")) {
                            if(result < 0 || result == 0) {
                                combinedDependants.add(tempTask);
                            }
                        }
                    }
                }
            }
//            If its a dependency
            if(isDep) { 
                currentJson.addDependants(pos, combinedDependants);
//            Else add as a conflict
            } else {
                for (int j = 0; j < combinedDependants.size(); j++) {
                    currentJson.addConflict(combinedDependants.get(j));
                }
            }
        }
    }
	
	private static List<Package> createPackages(JsonElement json, String[] initialArr) {
		
		List<Package> allTasks = new ArrayList<>();
		Gson gson = new Gson();
		Package[] finalPackage = gson.fromJson(json, Package[].class);
		
//		Put the initials in a map so we can later find them and install them
		Map<String, String> tempInitials = new HashMap<>();
		for (int i = 0; i < initialArr.length; i++) {
			Matcher m = toInitialPattern.matcher(initialArr[i]);
			String tempVersion = "0.0";
			if(m.matches()) {
				String tempPackageName = m.group(1);
				if (!(m.group(3) == null)) {
					tempVersion = m.group(3);
				}
				tempInitials.put(tempPackageName+tempVersion, tempVersion);
			}
			
		}

//		Add all packages to a list, including dependents
		for (Package e: finalPackage) {
//			Check if packages were in initial, if yes, set them as done
			String getInitial = tempInitials.get(e.name+e.version);
			if(getInitial != null) {
				if(getInitial.equals(e.version)) {
					e.done = true;
				}
			}
			allTasks.add(e);
			e.init();
        }

//		Using finalPackage here because dependencies are still in there
		for (Package e: finalPackage) {
		
//			Add to Dependents
			if (Arrays.deepToString(e.depends) != "null") {
	        	for (int i = 0; i < e.depends.length; i++) {
	        		String[] dependants = new String[e.depends[i].length];
					for (int j = 0; j < e.depends[i].length; j++) {	
						dependants[j] = e.depends[i][j];
					}	
					addDependAndConflict(dependants, e, allTasks, true, i);
	        	}
			}
			
//			Add to Constraints
			if (Arrays.deepToString(e.conflicts) != "null") {
				for (int i = 0; i < e.conflicts.length; i++) {
					String[] conflicts = {e.conflicts[i]};
					addDependAndConflict(conflicts, e, allTasks, false, i);
				}
			}
        }
//		Print everything		
//		for (int i = 0; i < allTasks.size(); i++) {
//			System.out.println(allTasks.get(i));	
//		}
		return allTasks;
	}
	
	public static void expandString(HashMap<String, List<Package>> getResults) {
		
//		We did a loop, and now we assign the vars from getResults	
		List<Package> toItterate = new ArrayList<>();
		for (Entry<String, List<Package>> p : getResults.entrySet()) {
//			Set solver initially to the first set.
			if(solver.equals("")) {
				solver = p.getKey();
			}
			toItterate = p.getValue();
		}
		String getPackageName = "";
		List<Package> getPackages= new ArrayList<>();
//		This should happen if there are no packageNames left.
		if(toItterate.size() == 0) {
			getResults(solver); 
		} else {
//			Looping the values
			for (int i = 0; i < toItterate.size(); i++) {
				Package pack = toItterate.get(i);
				HashMap<String, List<Package>> tempStorage = new HashMap<>();
//				Store results on each itteration
				tempStorage  = pack.addToBooleanString(solver);
//				extract results. for loop gets all the packs, 2nd loop gets the string
				for (Entry<String, List<Package>> tempPack : tempStorage.entrySet()) {
					getPackageName = tempPack.getKey();
					for (int j = 0; j < tempPack.getValue().size(); j++) {
						getPackages.add(tempPack.getValue().get(j));
					}
				}
				if(!getPackageName.equals("") && !getPackageName.contains("TEST999*")) {
					String checkNeg = "~" + getPackageName;
					String replaceNeg = checkNeg.replace(" & ", " & ~");
					
//					Replace with String	 
//					If there are more OR's, I need to replace entirely with new string
					int orCountSolver = solver.length() - solver.replace("|", "").length();
					int orCountString = getPackageName.length() - getPackageName.replace("|", "").length();
					if(orCountSolver < orCountString) {
						solver = getPackageName;
					} else {
						solver = " "+solver; 
						solver = solver.replace(" " + pack.name+pack.dotlessVersion, " " + getPackageName); 
						solver = solver.trim();
					}
//					Check to replace for negatives
					if(solver.contains(checkNeg)) {
						solver = solver.replace(checkNeg, replaceNeg);
//						Remove double negatives
						solver = solver.replace("~~", "~");
					}
				}
			}
			HashMap<String, List<Package>> getNewResults = new HashMap<>();
			getNewResults.put(solver, getPackages);
			expandString(getNewResults);
		}
	}
	
	public static void getResults(String finalAnswer) {
		final FormulaFactory f = new FormulaFactory();
		final PropositionalParser p = new PropositionalParser(f);
		Formula formula;
		try {
			formula = p.parse(finalAnswer);
		    final SATSolver miniSat = MiniSat.miniSat(f);
		    miniSat.add(formula);
		    List<Assignment> allPossibleResults = miniSat.enumerateAllModels();
		   
		    for (Assignment assignment : allPossibleResults) {
		    	if(assignment.size() > 0) {
			    	packagesToInstall = new ArrayList<>();
			    	packagesToUninstall = new ArrayList<>();
		    		Package getPackage = null;
		    		String[] posLit = assignment.positiveLiterals().toString().replace("[", "").replace("]", "").split(",");
		    		
		    		String[] negLit = new String[assignment.negativeLiterals().size() ];
		    		if(assignment.negativeLiterals().size() > 0) {
		    			negLit = assignment.negativeLiterals().toString().replace("[", "").replace("]", "").split(",");
		    		}
		    		
//		    		Adding packages to install 
		    		for (int i = 0; i < posLit.length; i++) {

//		    			This is below is not a bug, its a feature!!
		    			String[] temp = posLit[i].split("version999");
		    			String key = temp[0].trim();
		    			
		    			String value = temp[1].trim();
		    			SortedSet<Package> temp2 = tasksMap.get(key);
		    			for (Package pack : temp2) {
		    				String packVersion = pack.dotlessVersion.replace("version999", "");
		    				if(packVersion.equals(value)){
		    					getPackage = pack;
		    					break;
		    				}		
						}
		    			packagesToInstall.add(getPackage);
					}    		
//		    		Adding packages to uninstall

		    		for (int i = 0; i < negLit.length; i++) {
		    			String[] temp = negLit[i].split("version999");
		    			String key = temp[0].replace("~", "").trim();
		    			String value = temp[1].trim();
		    			SortedSet<Package> temp2 = tasksMap.get(key);
//		    			Checking for which conflicts exist
		    			for (Package pack : temp2) {
		    				String packVersion = pack.dotlessVersion.replace("version999", "");
		    				if(packVersion.equals(value)){
		    					getPackage = pack;
		    					break;
		    				}
		    			}
		    			
	    				for (Package asg : packagesToInstall) {
	    					if(asg.conflictsSet.size() > 0) {
	    						for (Package checkConflict : asg.conflictsSet) {
	    							String checkC = checkConflict.name+checkConflict.dotlessVersion;
	    							String compareC = getPackage.name+getPackage.dotlessVersion;
									if(checkC.equals(compareC)) {
										if(getPackage.done==true)
											packagesToUninstall.add(getPackage);
									}
								}
	    					}
	    				}		
		    		}
		    		if(mustBeUninstalled!=null) {
			    		for (Package pack : mustBeUninstalled) {
			    			if(pack.done==true)
								packagesToUninstall.add(pack);
						}
		    		}

//		    		Swapping the arraylist
		            int size = packagesToInstall.size();
		            for (int i = 0; i < size / 2; i++) {
		                final Package pack = packagesToInstall.get(i);
		                packagesToInstall.set(i,packagesToInstall.get(size-i-1));
		                packagesToInstall.set(size-i-1, pack);
		            }
		            
		            int getSize = 0;
		    		for (Package toUninstall : packagesToUninstall) {
						getSize += toUninstall.checkUninstall(getSize);
						
					}
		    		for (Package toInstall : packagesToInstall) {
						getSize += toInstall.checkInstall(getSize);
					}
		    		topoSort(getSize);
		    	}
			}
		} catch (ParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static DefaultDirectedGraph<String, DefaultEdge> createGraph() {
		DefaultDirectedGraph<String, DefaultEdge> finalGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
		for (Package pack : packagesToInstall) {
			finalGraph.addVertex(pack.name + "=" + pack.version);
		}
		
		for (Package pack : packagesToInstall) {
			for (int i = 0; i < pack.dependantSet.size(); i++) {
				for (int j = 0; j < pack.dependantSet.get(i).size(); j++) {
					Package depPack = pack.dependantSet.get(i).get(j);
					if(finalGraph.containsVertex(depPack.name+"="+depPack.version)) {
						finalGraph.addEdge(pack.name + "=" + pack.version, depPack.name + "=" + depPack.version);
					}
				}
			}
		}
		return finalGraph;
	}
	
	public static void topoSort(int getSize) {
		DefaultDirectedGraph<String, DefaultEdge> finalGraph = createGraph();
		TopologicalOrderIterator<String, DefaultEdge> order;
		CycleDetector<String, DefaultEdge> cycleDetector;
		
		cycleDetector = new CycleDetector<String, DefaultEdge>(finalGraph);
		StringBuilder currResult = new StringBuilder();
		if (cycleDetector.detectCycles()) {
//			Circular dependency
			return;
		} else {
			order = new TopologicalOrderIterator<String, DefaultEdge>(finalGraph);
			String currLocation;
			
			
			List<String> topoOrder = new ArrayList<>();
			while(order.hasNext())
			{
				String nextVal = order.next();
				currLocation = nextVal;
				topoOrder.add(currLocation);
			}
			
			
			currResult.append("[");

			if(packagesToUninstall!=null) {
				
				for(Package p : packagesToUninstall) {
					currResult = currResult.append('"').append("-").append(p.name).append("=").append(p.version).append('"').append(",\n");
				}
			}
			
			for(int i = topoOrder.size() - 1; i >= 0; i--) {
				currResult = currResult.append('"').append("+").append(topoOrder.get(i)).append('"').append(",");
				
			}
//			If its empty brackets, just let them be
			if(currResult.length() > 1)
				currResult = currResult.deleteCharAt(currResult.length()-1);
			currResult.append("]");
			
			if(lowestSize == 0) {
    			lowestSize = getSize;
    			finalResult = currResult;
    		} else {
    			if (lowestSize > getSize) {
    				lowestSize = getSize;
    				finalResult = currResult;
    			}
    		}
		
		}
	}
	
	
	
//	Checks which packages to run and starts running them
	public static void startRun(Map<String, SortedSet<Package>> tasksMap, String[] toInstallArr) {
		String installString = "";
		String tempString = "";
		List<Package>collectAllNext = new ArrayList<>();
		HashMap<String, List<Package>> toReturn = new HashMap<>(); 
		mustBeUninstalled = new ArrayList<Package>();
		for (int i = 0; i < toInstallArr.length; i++) {
//			
			Matcher m = toInstallPattern.matcher(toInstallArr[i]);
			
			if(m.matches()) {
				
				String tempInstall = m.group(1);
				String tempPackageName = m.group(2);
				SortedSet<Package> tempPackage = tasksMap.get(tempPackageName);
				boolean isSameVal = false;
				boolean ifExecuted = false;
//				TODO again, create a list of all the possible values, check which one has more/less dependencies to run that one
				for (Package p : tempPackage) {
					collectAllNext.add(p);
					
					if(isSameVal && !ifExecuted) {
						tempString = tempString.substring(0, tempString.length()-2);
						tempString += " | ";
						ifExecuted = true;
					}
					
					if(tempInstall.equals("-")) { 
						mustBeUninstalled.add(p);
						tempString += "~"+p.name+p.dotlessVersion + " & ";
					} else {
						if(isSameVal) {
							tempString += p.name+p.dotlessVersion + " | ";
						} else { 
							tempString += p.name+p.dotlessVersion + " & ";
							if(!installString.equals(""))
								tempString = installString;
						}
					}
					
					isSameVal = true;
				}
			}
		}
				tempString = tempString.substring(0, tempString.length()-2);
				
				toReturn.put(tempString,  collectAllNext);
				expandString(toReturn);
	}
}