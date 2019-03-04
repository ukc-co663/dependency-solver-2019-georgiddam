package depsolver;
import java.io.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

import org.logicng.datastructures.Assignment;
import org.logicng.datastructures.Tristate;
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
	
	private static HashMap<String, List<Package>> getResults = new HashMap<>();
	
	private static Map<String, SortedSet<Package>> tasksMap = new HashMap<>();
	
	private static int lowestSize;
	
//	private static String finalResult;
	
	private static List<Package> finalPackages = new ArrayList<>();
	
	private static List<Package> packagesToInstall;
	private static List<Package> packagesToUninstall;
	
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
//		System.out.println(packagesToUninstall);
		topoSort();
//		System.out.println(finalResult);
		
	}
	
	  static String readFile(String filename) throws IOException {
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
	
//	Add dependencies and constraints
	private static void addDependAndConflict(String[] dependants, Package currentJson, List<Package> allTasks, boolean isDep, int pos) { 
        List<Package> combinedDependants = new ArrayList<>();
        String name;
        String version = "0.0";
        String symbol = "=";
        for (int i = 0; i < dependants.length; i++) {
            String depends = dependants[i];;
            Matcher m = dependancyConflictPattern.matcher(depends);
            if(m.matches()) {
//                System.out.println("Matching: " +  m.group(0) + " | " + m.group(1) + " | " + m.group(2) + " | " + m.group(3) + "");
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
                        int result = tempTask.version.compareTo(version);
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
//					System.out.println("What is e: " + dependants[0]);
					addDependAndConflict(dependants, e, allTasks, true, i);
	        	}
			}
			
//			Add to Constraints
			if (Arrays.deepToString(e.conflicts) != "null") {
				for (int i = 0; i < e.conflicts.length; i++) {
					String[] conflicts = {e.conflicts[i]};
//					System.out.println(e.conflicts[i]);
					addDependAndConflict(conflicts, e, allTasks, false, i);
				}
			}
			
        }   
				
//		for (int i = 0; i < allTasks.size(); i++) {
////			Shows me the Linked Dependencies in the end
//			System.out.println(allTasks.get(i));
//			
//		}
//		
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
//			solver = solver.replaceAll("\\.","");
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
//					Setup negative values
					String checkNeg = "~" + getPackageName;
					String replaceNeg = checkNeg.replace(" & ", " & ~");
					
					
//					Look at what we just got back and check each value for possible circular dependency
					String[] tempCheck = getPackageName.split("&");
					
					String s = "";
					String s2 = "";
					for (int j = 1; j < tempCheck.length; j++) {
						s = tempCheck[j];			
						if(solver.contains(s)) {
							s2 = "~" + s;
							s2 = s2.replace(" ", "");
						} else {
							s="";
						}
					}
					
//					Replace with String	 
//					If there are more OR's, I need to replace entirely with new string
					int orCountSolver = solver.length() - solver.replace("|", "").length();
					int orCountString = getPackageName.length() - getPackageName.replace("|", "").length();
					if(orCountSolver < orCountString) {
						solver = getPackageName;
					} else {
						solver = solver.replace(pack.name+pack.dotlessVersion, getPackageName); 
					}
					
//					Check to replace for negatives
					if(solver.contains(checkNeg)) {
						solver = solver.replace(checkNeg, replaceNeg);
//						Remove double negatives
						solver = solver.replace("~~", "~");
					}

//					TODO Possible issue if there is no " | " I don't know how it will react
					String[] check = solver.split("\\|");
					
						for (int j = 0; j < check.length; j++) {
							if(!(s.trim().equals(""))) {
//								Get Opposite
								String getNegative = "";
								String getPositive = "";
								if(s.contains("~")) {
									getPositive = s.replace("~", "");
									getNegative = s;
								} else {
									getPositive = s;
									getNegative = "~"+s.trim();
								}
//							If there are 2 positives, then it needs to install the same thing to get it which is circular dependency
											
							if(check[j].contains(getPositive) && !check[j].contains(getNegative)) {
								String replace = check[j] + "| ";
								if(j >= check.length-1) {
									replace = "|" + check[j];
								}
								solver = solver.replace(replace, "");
							}
						}
					}
					if(!(s.trim().equals(""))) {
//						Replacing dependencies
						solver = solver.replace(s2, s);
					}
				}
			}
//			Simplify
			FormulaFactory f = new FormulaFactory();
			PropositionalParser p = new PropositionalParser(f);
			Formula temp = null;
//			solver = solver.replace(".", "");
			try { 
				temp = p.parse(solver);
			} catch (ParserException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(temp == null) {
				System.out.println("Error");
			} else {
				solver = temp.toString();
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
			
//			System.out.println();
			
		    final SATSolver miniSat = MiniSat.miniSat(f);
		    miniSat.add(formula);
//		    final Tristate result = miniSat.sat();
		    List<Assignment> allPossibleResults = miniSat.enumerateAllModels();
		   
		    for (Assignment assignment : allPossibleResults) {
		    	if(assignment.size() > 0) {
//		    		System.out.println(assignment.literals());
			    	packagesToInstall = new ArrayList<>();
			    	packagesToUninstall = new ArrayList<>();
//			    	System.out.println(packagesToUninstall);
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
//		    			System.out.println(key + " " + value);
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
//		    			System.out.println(key);
		    			String value = temp[1].trim();
		    			SortedSet<Package> temp2 = tasksMap.get(key);
//		    			Checking for which conflicts exist
		    			for (Package pack : temp2) {
		    				String packVersion = pack.dotlessVersion.replace("version999", "");
		    				if(packVersion.equals(value)){
//		    					System.out.println(getPackage);
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

		    		
//		    		StringBuilder result = new StringBuilder();
//		    		result.append("[");
		    		
		    		
		    		
//		    		Swapping the arraylist
		            int size = packagesToInstall.size();
		            for (int i = 0; i < size / 2; i++) {
		                final Package pack = packagesToInstall.get(i);
		                packagesToInstall.set(i,packagesToInstall.get(size-i-1));
		                packagesToInstall.set(size-i-1, pack);
		            }
		            
		            int getSize = 0;
//		    		packagesToInstall.reverse();
		    		for (Package toUninstall : packagesToUninstall) {
//		    			System.out.println(getSize);
//		    			System.out.println("uninstall " + toUninstall);
						getSize += toUninstall.checkUninstall(getSize);
//						System.out.println(result);
						
					}
		    		for (Package toInstall : packagesToInstall) {
						getSize += toInstall.checkInstall(getSize);
					}
//		    		result = result.deleteCharAt(result.length()-2);
//		    		result.append("]");
//		    		System.out.println(result);

//		    		Get best name
		    		if(lowestSize == 0) {
		    			lowestSize = getSize;
		    			finalPackages = packagesToInstall;
		    		} else {
		    			if (lowestSize > getSize) {
		    				lowestSize = getSize;
		    				finalPackages = packagesToInstall;
		    			}
		    		}
//		    		System.out.println(finalPackages);
//		    		System.out.println(getSize);
		    	}
			}
		} catch (ParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static DefaultDirectedGraph<String, DefaultEdge> createGraph() {
		DefaultDirectedGraph<String, DefaultEdge> finalGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
		
		for (Package pack : finalPackages) {
			finalGraph.addVertex(pack.name + "=" + pack.version);
		}
		
		for (Package pack : finalPackages) {
			for (int i = 0; i < pack.dependantSet.size(); i++) {
				for (int j = 0; j < pack.dependantSet.get(i).size(); j++) {
					Package depPack = pack.dependantSet.get(i).get(j);
					if(finalGraph.containsVertex(depPack.name+"="+depPack.version)) {
						finalGraph.addEdge(pack.name + "=" + pack.version, depPack.name + "=" + depPack.version);
					}
				}
			}
		}
//		System.out.println(finalGraph);
		return finalGraph;
	}
	
	public static void topoSort() {
		DefaultDirectedGraph<String, DefaultEdge> finalGraph = createGraph();
		TopologicalOrderIterator<String, DefaultEdge> order;
		CycleDetector<String, DefaultEdge> cycleDetector;
		
		cycleDetector = new CycleDetector<String, DefaultEdge>(finalGraph);
		
//		Just in case
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
			
			StringBuilder finalResult = new StringBuilder();
			finalResult.append("[");
			if(packagesToUninstall.size() > 0) {
				for(Package p : packagesToUninstall) {
					finalResult = finalResult.append('"').append("-").append(p.name).append("=").append(p.version).append('"').append(",\n");
				}
			}
			
			for(int i = topoOrder.size() - 1; i >= 0; i--) {
				finalResult = finalResult.append('"').append("+").append(topoOrder.get(i)).append('"').append(",");
				
			}
			
			finalResult = finalResult.deleteCharAt(finalResult.length()-1);
			finalResult.append("]");
			System.out.println(finalResult);
		}
	}
	
//	Checks which packages to run and starts running them
	public static void startRun(Map<String, SortedSet<Package>> tasksMap, String[] toInstallArr) {
		List<Package> toInstallList = new ArrayList<>();
		for (int i = 0; i < toInstallArr.length; i++) {
			Matcher m = toInstallPattern.matcher(toInstallArr[i]);
			
			if(m.matches()) {

				String tempInstall = m.group(1);
				String tempPackageName = m.group(2);
				String tempSymbol = m.group(3);
				String tempVersion = m.group(4);
				
				SortedSet<Package> tempPackage = tasksMap.get(tempPackageName);
//				TODO again, create a list of all the possible values, check which one has more/less dependencies to run that one
				for (Package p : tempPackage) {
					int result = 0;
					if(tempVersion == null) {
						result = 0;
						tempSymbol = "="; 
					} else {
						result = p.version.compareTo(tempVersion);
					}
				
					if(tempSymbol.equals("=")) {
						
						if(result == 0) {
							if(tempInstall.equals("+")) {
								toInstallList.add(p);
								expandString(getResults);
							}
						}
						
					} else if(tempSymbol.equals( "<")) {
						
						if(result == -1) {
							if(tempInstall.equals("+")) {
								toInstallList.add(p);
								expandString(getResults);
							}
						}
						
					} else if(tempSymbol.equals("<=")) {
						if(result == -1 || result == 0) {
							if(tempInstall.equals("+")) {
								toInstallList.add(p);
								expandString(getResults);
							}
						}
						
					} else if(tempSymbol.equals(">")) {
						
						if(result == 1) {
							if(tempInstall.equals("+")) {
								toInstallList.add(p);
								expandString(getResults);
							}
						}
						
					} else if(tempSymbol.equals(">=")) {
						if(result == 1 || result == 0) {
							if(tempInstall.equals("+")) {
								toInstallList.add(p);
							}
						}
					} else {
						System.out.println("Should never reach here: \"" + tempSymbol + "\"");
						break;
					}
				}
			};
		}
		
		for (Package toInstall : toInstallList) {
//			Reset solver for each itteration
			solver = "";
			String startString = toInstall.name+toInstall.dotlessVersion;
			getResults = toInstall.addToBooleanString(startString);
			expandString(getResults);
		}	
	}
}