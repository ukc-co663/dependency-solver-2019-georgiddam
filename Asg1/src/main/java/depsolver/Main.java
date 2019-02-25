package depsolver;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
	
	private static String solver = "(";
	
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
		Map<String, SortedSet<Package>> tasksMap = new HashMap<>();
	
		for (Package p: allTasks) {
			String pName = p.name;
			
			SortedSet<Package> temp = tasksMap.get(pName);
			if(temp == null) {
				temp = new TreeSet<>(PackageVersionComparator);
				tasksMap.put(pName, temp);
			}
			temp.add(p);
//			System.out.println(p);
//			Shorter version of the above 6 lines
//			tasksMap.computeIfAbsent(pName, _n -> new ArrayList<>()).add(p);			
			
		}
//		System.out.println();
		
		startRun(tasksMap, toInstallArr);
//		System.out.println(Arrays.toString(toInstallArr));
		System.out.println("Solver is: " + solver);
		final FormulaFactory f = new FormulaFactory();
		final PropositionalParser p = new PropositionalParser(f);
		final Formula formula = p.parse(solver);
		System.out.println(formula);
		
	    final SATSolver miniSat = MiniSat.miniSat(f);
	    miniSat.add(formula);
	    final Tristate result = miniSat.sat();
	    List<Assignment> allPossibleResults = miniSat.enumerateAllModels();
	    
	    System.out.println("All results " + allPossibleResults);
	    
	    
//		final Formula formula = p.parse("(B32 | C1) & D1031 & ~B30 & ~B32 & ~B32");
//		System.out.println("What is+ " + formulaSolver);
//		System.out.println("What is formula" + formula);
	}
	
	static void convertPackageToBool(String[] toInstall) {
		
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
//			System.out.println("Looking at: " + e.name + e.version);
//			System.out.println("Check conflicts: " +Arrays.deepToString(e.conflicts));
			if (Arrays.deepToString(e.conflicts) != "null") {
				for (int i = 0; i < e.conflicts.length; i++) {
					String[] conflicts = {e.conflicts[i]};
//					System.out.println(e.conflicts[i]);
					addDependAndConflict(conflicts, e, allTasks, false, i);
				}
			}
			
        }   
//		System.out.println(solver.substring(0, solver.length() -2));
				
//		for (int i = 0; i < allTasks.size(); i++) {
//			Shows me the Linked Dependencies in the end
//			System.out.println(allTasks.get(i));
			
//		}
//		
		return allTasks;
		
	}
	
	public static void expandString(Package packageToCheck) {
		for (int i = 0; i < allTasks.size(); i++) {
			String toReplace = "";
			
			Package getPackage = allTasks.get(i); 
			
			String packageString = getPackage.name+getPackage.version;
			String toInstallString = packageToCheck.name + packageToCheck.version;
			toReplace += packageString + " ";
			
			toReplace += getPackage.addToBooleanString("");

			if(!packageString.equals(toInstallString)) {
//				System.out.println( "toReplacE: " + toReplace);
				solver = solver.replace(packageString, toReplace);
//				System.out.println(solver);
			}
		}
		solver = solver.replaceAll("\\.","");
//		System.out.println(" What i get at the end: " + solver);
	}
	
	
	
	
//	Checks which packages to run and starts running them
	public static void startRun(Map<String, SortedSet<Package>> tasksMap, String[] toInstallArr) {
		StringBuilder strBuilder = new StringBuilder();
		List<Package> toInstallList = new ArrayList<>();
		List<Package> toRemoveList = new ArrayList<>();
		for (int i = 0; i < toInstallArr.length; i++) {
			Matcher m = toInstallPattern.matcher(toInstallArr[i]);
			
			if(m.matches()) {

				String tempInstall = m.group(1);
				String tempPackageName = m.group(2);
				String tempSymbol = m.group(3);
				String tempVersion = m.group(4);
				
				SortedSet<Package> tempPackage = tasksMap.get(tempPackageName);
				
	//			We check if a version exists, if not, then just run the value with highest version
	//			TODO I need to somehow check for all these available versions it might have, which ones to run
				if(tempVersion == null) {
					if(tempInstall.equals("+")) {
						toInstallList.add(tempPackage.first());
						String getStr = tempPackage.first().name+tempPackage.first().version;
						solver += tempPackage.first().addToBooleanString(getStr);
						
						expandString(tempPackage.first());
					} else {
						toRemoveList.add(tempPackage.first());
//						tempPackage.first().uninstall(strBuilder);
					}
				} else {
	//				If it has a version, look at the comparator, run the first version which satisfies it
	//				TODO again, create a list of all the possible values, check which one has more/less dependencies to run that one
					for (Package p : tempPackage) {
						
						int result = p.version.compareTo(tempVersion);
					
						if(tempSymbol.equals("=")) {
						
							if(result == 0) {
								if(tempInstall.equals("+")) {
//									toInstallList.add(p);
//									p.run(strBuilder);
//									System.out.println("What will it run" + p);
									String getStr = (p.name+p.version);
									solver += p.addToBooleanString(getStr);
									expandString(p);
								} else {
//									toRemoveList.add(p);
//									System.out.println("What will it run" + p);
//									p.uninstall(strBuilder);
								}
//								break;
							}
							
						} else if(tempSymbol.equals( "<")) {
							
							if(result == -1) {
								if(tempInstall.equals("+")) {
//									toInstallList.add(p);
									String getStr = (p.name+p.version);
									solver += p.addToBooleanString(getStr);
									expandString(p);
//									System.out.println("What will it run" + p);
//									p.run(strBuilder);
								} else {
//									toRemoveList.add(p);
//									System.out.println("What will it run" + p);
//									p.uninstall(strBuilder);
								}
//								break;
							}
							
						} else if(tempSymbol.equals("<=")) {
							if(result == -1 || result == 0) {
								if(tempInstall.equals("+")) {
//									toInstallList.add(p);
									String getStr = (p.name+p.version);
									solver += p.addToBooleanString(getStr);
									expandString(p);
//									System.out.println("What will it run" + p);
//									p.run(strBuilder);
								} else {
//									toRemoveList.add(p);
//									p.uninstall(strBuilder);
								}
//								break;
							}
							
						} else if(tempSymbol.equals(">")) {
							
							if(result == 1) {
								if(tempInstall.equals("+")) {
//									toInstallList.add(p);
									String getStr = (p.name+p.version);
									solver += p.addToBooleanString(getStr);
									expandString(p);
//									System.out.println("What will it run" + p);
//									p.run(strBuilder);
								} else {
//									toRemoveList.add(p);
//									System.out.println("What will it run" + p);
//									p.uninstall(strBuilder);
								}
//								break;
							}
							
						} else if(tempSymbol.equals(">=")) {
							if(result == 1 || result == 0) {
								if(tempInstall.equals("+")) {
//									toInstallList.add(p);
									String getStr = (p.name+p.version);
									solver += p.addToBooleanString(getStr);
									expandString(p);
//									System.out.println("What will it run" + p);
//									p.run(strBuilder);
								} else {
//									toRemoveList.add(p);
//									p.uninstall(strBuilder);
								}
//								break;
							}
							
						} else {
							System.out.println("Should never reach here: \"" + tempSymbol + "\"");
							break;
						}
					}
				}
			
			};
		}
//		checkIfInstalled(toInstallList, toRemoveList);
//		System.out.println("Installed Packages:\n" + strBuilder);	
//		Prints out my sets
//		for(Entry<String, SortedSet<Package>> e : tasksMap.entrySet()) {
//			System.out.println(e.getKey());
//			System.out.println(e.getValue().stream().map(Package::toString).collect(Collectors.joining(", ","[","]")));
//		}
		
	}
	
	
	public static void checkIfInstalled(List<Package> toInstallList, List<Package> toRemoveList) {
//		System.out.println("To Install List");
//		for (int i = 0; i < toInstallList.size(); i++) {
//			System.out.println(toInstallList.get(i));
//		}
//		System.out.println("To Remove List");
//		for (int i = 0; i < toRemoveList.size(); i++) {
//			System.out.println(toRemoveList.get(i));
//		}
		
	}

}