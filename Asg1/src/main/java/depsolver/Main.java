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
//		System.out.println("Solver before formula is: " + solver);
		final FormulaFactory f = new FormulaFactory();
		final PropositionalParser p = new PropositionalParser(f);
//		 A & B & D & ~C | A & C &  ~B & ~D
		final Formula formula = p.parse(solver);
		System.out.println("Formula is              : " + formula);
		
	    final SATSolver miniSat = MiniSat.miniSat(f);
	    miniSat.add(formula);
	    final Tristate result = miniSat.sat();
	    List<Assignment> allPossibleResults = miniSat.enumerateAllModels();
	    
	    System.out.println("All results " + allPossibleResults);
	    
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
	
	public static void expandString(HashMap<String, List<Package>> getResults) {
//		System.out.println("Set ");
//		We did a loop, and now we assign the vars from getResults	
		List<Package> toItterate = new ArrayList<>();
		for (Entry<String, List<Package>> p : getResults.entrySet()) {
//			Set solver initially to the first set.
			if(solver.equals("")) {
				solver = p.getKey();
				System.out.println("Solver is: " + solver);
			}
			toItterate = p.getValue();
		}
		
		String getStrings = "";
		List<Package> getPackages= new ArrayList<>();
		
	
		
		if(toItterate.size() == 0) {
			solver = solver.replaceAll("\\.","");
//			System.out.println("Finished, what is solver: " + solver);
		} else {
//			Looping the values
			for (int i = 0; i < toItterate.size(); i++) {
				Package pack = toItterate.get(i);
				HashMap<String, List<Package>> tempStorage = new HashMap<>();
//				Store results on each itteration
				tempStorage  = pack.addToBooleanString(solver);
//				extract results. for loop gets all the packs, 2nd loop gets the string
				for (Entry<String, List<Package>> tempPack : tempStorage.entrySet()) {
					getStrings = tempPack.getKey();
					for (int j = 0; j < tempPack.getValue().size(); j++) {
						getPackages.add(tempPack.getValue().get(j));
					}
				}
//				int getLength = getStrings.length;
				
//				System.out.println("string is " + getStrings);
				

			
				if(!getStrings.equals("") && !getStrings.contains("TEST999*")) {
//					Setup negative values
					String checkNeg = "~" + getStrings;
					String replaceNeg = checkNeg.replace(" & ", " & ~");
					
					
//					Check for circular dependency, what do we do if we find one
					String[] tempCheck = getStrings.split("&");
					
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
					
					int orCountSolver = solver.length() - solver.replace("|", "").length();
					int orCountString = getStrings.length() - getStrings.replace("|", "").length();
					if(orCountSolver < orCountString) {
						solver = getStrings;
					} else {
						solver = solver.replace(pack.name+pack.version, getStrings); 
					}
					
					
					
//					System.out.println("At start end is : " + solver);
//					Check to replace for negatives
					if(solver.contains(checkNeg)) {
						solver = solver.replace(checkNeg, replaceNeg);
//						Remove double negatives
						solver = solver.replace("~~", "~");
					}
					
					System.out.println("At start " + solver);

//					TODO Possible issue if there is no " | " I don't know how it will react
					String[] check = solver.split("\\|");
//					System.out.println("Check is " + Arrays.toString(check));
					String ss = s.replace(" ", "");
					
						for (int j = 0; j < check.length; j++) {
							if(!(s.trim().equals(""))) {
//								Get Opposite
//								String getOpposite = ("~"+ss);
								String getNegative = "";
								String getPositive = "";
								if(s.contains("~")) {
									getPositive = s.replace("~", "");
									getNegative = s;
								} else {
									getPositive = s;
									getNegative = "~"+s.trim();
								}
//								System.out.println("Negaitve : Positive " + getNegative + " " + getPositive );
//							If there are 2 positives, then it needs to install the same thing to get it which is circular dependency
							
							
							if(check[j].contains(getPositive) && !check[j].contains(getNegative)) {
//								System.out.println("Check is " + check[j]);
//								String tempSolver = check[j].replace(getNegative, "");
//								System.out.println("TempSolver " + tempSolver);
//								int dupes = tempSolver.length() - tempSolver.replace(getPositive, "").length();
//								System.out.println("Duplicates " + dupes);
//							
								String replace = check[j] + "| ";
//								System.out.println("Size " + j + " " + (check.length-1));
								if(j >= check.length-1) {
//									System.out.println("Trigger");
									replace = "|" + check[j];
								}
//								System.out.println("Check " + check[j]);
								
//								System.out.println("Replacing with " + replace);
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
			solver = solver.replace(".", "");
			try { 
				temp = p.parse(solver);
//				System.out.println(temp);
			} catch (ParserException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(temp == null) {
				System.out.println("Error");
			} else {
				solver = temp.toString();
			}
			
			System.out.println("Solver at end is: " + solver);
			HashMap<String, List<Package>> getNewResults = new HashMap<>();
			getNewResults.put(solver, getPackages);
			expandString(getNewResults);
		}
//		for (int l = 0; l < 2; l++) {
			
		
//		for (int i = 0; i < allTasks.size(); i++) {
//			String toReplace = "";
//			
//			Package getPackage = allTasks.get(i); 
//			
//			String packageString = getPackage.name+getPackage.version;
//			String toInstallString = packageToCheck.name + packageToCheck.version;
//			toReplace += packageString + " ";
//			
//			toReplace += getPackage.addToBooleanString("");
//			
////			System.out.println("Looking for dont" + toReplace);
////			
////			System.out.println(toReplace.contains("dontReplaceThis"));
//
//			if(!toReplace.contains("dontReplaceThis")) {
////				System.out.println("What am I replacing" + packageString);
////				System.out.println("What am I replacing with:" + toReplace);
////				System.out.println( "toReplacE: " + toReplace);
//				solver = solver.replace(packageString, toReplace);
////				System.out.println(solver);
//			}
//		}
//		}
//		solver = solver.replaceAll("\\.","");
//		System.out.println(" What i get at the end: " + solver);
	}
	
	
	
	
//	Checks which packages to run and starts running them
	public static void startRun(Map<String, SortedSet<Package>> tasksMap, String[] toInstallArr) {
//		StringBuilder strBuilder = new StringBuilder();
		List<Package> toInstallList = new ArrayList<>();
//		List<Package> toRemoveList = new ArrayList<>();
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
//						String getStr = tempPackage.first().name+tempPackage.first().version;
//						getResults = tempPackage.first().addToBooleanString();
						
						expandString(getResults);
					} else {
//						toRemoveList.add(tempPackage.first());
//						tempPackage.first().uninstall(strBuilder);
					}
				} else {
	//				If it has a version, look at the comparator, run the first version which satisfies it
	//				TODO again, create a list of all the possible values, check which one has more/less dependencies to run that one
					for (Package p : tempPackage) {
						System.out.println("Trigger");
						int result = p.version.compareTo(tempVersion);
					
						if(tempSymbol.equals("=")) {
							
							if(result == 0) {
								if(tempInstall.equals("+")) {
									toInstallList.add(p);
//									p.run(strBuilder);
//									System.out.println("What will it run" + p);
//									String getStr = (p.name+p.version);
//									getResults = p.addToBooleanString();
									expandString(getResults);
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
									toInstallList.add(p);
//									String getStr = (p.name+p.version);
//									getResults = p.addToBooleanString();
									expandString(getResults);
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
									toInstallList.add(p);
//									String getStr = (p.name+p.version);
//									getResults = p.addToBooleanString();
									expandString(getResults);
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
									toInstallList.add(p);
//									String getStr = (p.name+p.version);
//									getResults = p.addToBooleanString();
									expandString(getResults);
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
									toInstallList.add(p);
//									String getStr = (p.name+p.version);
									
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
		
		for (Package toInstall : toInstallList) {
			String startString = toInstall.name+toInstall.version;
			getResults = toInstall.addToBooleanString(startString);
			expandString(getResults);
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