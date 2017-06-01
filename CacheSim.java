import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.*;


public class CacheSim {
	
	enum cache_type {
		DIRECT_MAPPED {public String toString() {return "Direct Mapped";}},
		SET_ASSOCIATIVE {public String toString() {return "Set Associative";}},
		FULLY_ASSOCIATIVE {public String toString() {return "Fully Associative";}};
	}
	
	public class line {
		boolean valid_bit;
		int age;
		int tag;
	}
	
	public class set {
		public List<line> lines = new ArrayList<line>();
		
		void printContents() {
			int lc = 0;
			for(line l : lines) {
				System.out.println("\tLINE "+lc+"\t valid:"+l.valid_bit+" tag:" + l.tag + " (age:" + l.age +")" );
				lc++;
			}
		}
	}
	
	public class cache {
		public List<set> sets = new ArrayList<set>();
		
		cache_type type;
		int cache_lines_total;
		int cache_sets_total;
		int cache_lines_per_set;
		int set_index_bits;
		
		cache(int cache_lines, int E) {
			this.cache_lines_total = cache_lines;
			this.cache_lines_per_set = E;
			setup();
		}
		
		void setup() {
			
			if(cache_lines_per_set == 1) {
				type = cache_type.DIRECT_MAPPED;
				cache_sets_total = cache_lines_total;
				
			} else if (cache_lines_per_set == cache_lines_total) {
				type = cache_type.FULLY_ASSOCIATIVE;
				cache_sets_total = 1;
				
			} else {
				type = cache_type.SET_ASSOCIATIVE;
				cache_sets_total = cache_lines_total / cache_lines_per_set;
			}
			
			set_index_bits = (int)(Math.log(cache_sets_total)/Math.log(2) + 1e-10);
			
			for(int s=0; s < cache_sets_total; s++) {
				set set = new set();
				for(int l=0; l < cache_lines_per_set; l++) {
					set.lines.add(l,new line());
				}
				sets.add(s,set);
			}
		}
		
		void printCache() {
			int sc=0;
			System.out.println("\nCache (" + type + ") :");
			for(set set : sets) {
				System.out.println("Set " + sc + ":\t");
				set.printContents();
				sc++;
			}
			System.out.println("\n");
		}	
	}
	
	public static PrintWriter writer;
	
	public static void main(String[] args) {
		
		try {
			writer = new PrintWriter("cachesim.log", "UTF-8");
		} catch (IOException exception) {
			System.out.println(exception.getMessage());
		}
		
		boolean verbose_mode = false; // v
		int anzahl_index_bits = 0;	// s
		int anzahl_cache_bloeke = 0; // S = 2^s
		int anzahl_bloeke_pro_satz = 0; // E
		int anzahl_block_bits = 0; // b
		String tracefile = ""; // t
		
	    Option s_anzahl_index_bits = Option.builder("s").required().argName("anzahl_index_bits").hasArg().desc("amount of index bits").build();
	    Option E_anzahl_bloeke_pro_satz = Option.builder("E").required().argName("anzahl_bloeke_pro_satz").hasArg().desc("amount of blocks per sentence").build();
	    Option b_anzahl_block_bits = Option.builder("b").required().argName("anzahl_block_bits").hasArg().desc("amount of block bits").build();
	    Option t_tracefile = Option.builder("t").required().argName("tracefile").hasArg().desc("valgrind tracefile").build();
	    Option v_verbose_mode = Option.builder("v").longOpt("verbose_mode").desc("activate verbose mode").build();
	    Option h_help = Option.builder("h").longOpt("help").desc("display help").build();
	    
	    Options options = new Options();
	    options.addOption(s_anzahl_index_bits);
	    options.addOption(E_anzahl_bloeke_pro_satz);
	    options.addOption(b_anzahl_block_bits);
	    options.addOption(t_tracefile);
	    options.addOption(v_verbose_mode);
	    options.addOption(h_help);
	    
	    
	    String[] testArgs = {"-v","-s", "4", "-E", "8", "-b", "4", "-t", "trace1"};
	    
	    HelpFormatter formatter = new HelpFormatter();
	    
	    try
	    {	
	    	CommandLine commandLine;
	    	CommandLineParser parser = new DefaultParser();
	        commandLine = parser.parse(options, testArgs);
	        
	        if (commandLine.hasOption("h")) {      
	    	    formatter.printHelp("CacheSim", "Arguments:\n", options, "\n", true);
	    	    System.exit(0);
	        }

	        if (commandLine.hasOption("s")) {
	        	
	            anzahl_index_bits = Integer.parseInt(commandLine.getOptionValue("s"));	// s
	    		anzahl_cache_bloeke = 1 << anzahl_index_bits;
	    		writer.println("Option s set: " + anzahl_index_bits);
	        }

	        if (commandLine.hasOption("E")) {
	        	
	            anzahl_bloeke_pro_satz = Integer.parseInt(commandLine.getOptionValue("E"));
	            writer.println("Option E set: " + anzahl_bloeke_pro_satz);
	        }
	        
	        if (commandLine.hasOption("b")) {
	        	
	            anzahl_block_bits = Integer.parseInt(commandLine.getOptionValue("b")); // b
	            writer.println("Option b set: " + anzahl_block_bits);
	        }

	        if (commandLine.hasOption("t")) {
	        	      
	            tracefile = commandLine.getOptionValue("t");
	            writer.println("Option t set: " + tracefile);
	        }
	        
	        if (commandLine.hasOption("v")) {
	        	
	            verbose_mode = true;
	            writer.println("Option v set: " +  verbose_mode);
	        }
	        
	        writer.println("----\n");
	        
	    } catch (ParseException exception) {
	    	
	        System.out.println(exception.getMessage() + "\n\n");
    	    formatter.printHelp("CacheSim", "\n\nArguments:\n", options, "\n", true);
    	    System.exit(0);
	    }
	    
			
		Matcher matcher;
		Pattern pattern = Pattern.compile(" ([SLM]) ([0-9, a-f]{8}),([0-9]{1,})");
		
		// setup new cache
		cache cache = new CacheSim().new cache(anzahl_cache_bloeke, anzahl_bloeke_pro_satz);
		
		// status variables
		int timestamp = 0, hits=0, misses=0, evictions=0;
		
		try (BufferedReader br = new BufferedReader(new FileReader(new File(tracefile + ".txt")))) {
			
		    String line;
		    while ((line = br.readLine()) != null) {
		    	
		       matcher = pattern.matcher(line);
		       
		       if(matcher.matches()) {
		    	   
		           	// get and split line info 
		    	   	String action = matcher.group(1);
		    	   	String hex_addr = matcher.group(2);
		    	   	String byte_load = matcher.group(3);
		    	   	
		    	   	// address
		    	   	long addr = (Long.parseLong(hex_addr, 16));
					String bin_addr = Long.toBinaryString(addr);
					
					// tag
					String tag = Long.toBinaryString(addr >> anzahl_block_bits + cache.set_index_bits);
					int tagAsInt = Integer.parseInt(tag, 2);
					
					// index 
					String index = "0"; 	// if fully associative is zero
											// if not fully associative
					if(cache.type != cache_type.FULLY_ASSOCIATIVE ) {	
						long temp_index = addr <<  64 - anzahl_block_bits - cache.set_index_bits;
						index = Long.toBinaryString(temp_index >>> 64 - cache.set_index_bits);
					}
					
					int indexAsInt = Integer.parseInt(index,2);
					
					
					//printAddressDetails(bin_addr, tag, index, tagAsInt, indexAsInt);
					
					
					boolean found_place = false, hit_flag=false, miss_flag=false, eviction_flag=false;
					int youngest = Integer.MAX_VALUE;
					int toEvict = 0;
					
					for (int l = 0; l < cache.cache_lines_per_set; l++) {
						
						
						if (cache.sets.get(indexAsInt).lines.get(l).valid_bit == false) {

							miss_flag = true;

							cache.sets.get(indexAsInt).lines.get(l).valid_bit = true;
							cache.sets.get(indexAsInt).lines.get(l).age = timestamp;
							cache.sets.get(indexAsInt).lines.get(l).tag = tagAsInt;
							found_place = true;
							break;

						} else {

							if (cache.sets.get(indexAsInt).lines.get(l).tag == tagAsInt) {

								hit_flag = true;

								cache.sets.get(indexAsInt).lines.get(l).age = timestamp;
								found_place = true;
								break;

							} else {

								if (cache.type == cache_type.DIRECT_MAPPED) {
							
									miss_flag = true;
									eviction_flag = true;

									cache.sets.get(indexAsInt).lines.get(0).tag = tagAsInt;
									cache.sets.get(indexAsInt).lines.get(0).age = timestamp;
								}
							}
						}
						
						if (youngest > cache.sets.get(indexAsInt).lines.get(l).age) {
							youngest = cache.sets.get(indexAsInt).lines.get(l).age;
							toEvict = l;
						}

						
					}
						
					if (cache.type != cache_type.DIRECT_MAPPED && !found_place ) {
						
						// evict oldest line
						miss_flag = true;
						eviction_flag = true;
						
						cache.sets.get(indexAsInt).lines.get(toEvict).valid_bit = true;
						cache.sets.get(indexAsInt).lines.get(toEvict).age = timestamp;
						cache.sets.get(indexAsInt).lines.get(toEvict).tag = tagAsInt;
					}
					
					if(action.equals("M")) {
						hit_flag = true;
					}
					
					if(hit_flag) 
						hits++;
					if(miss_flag)
						misses++;
					if(eviction_flag)
						evictions++;
					
					if(verbose_mode) {
						
						String hit="",miss="",eviction="";
						
						if(hit_flag) 
							hit="hit";
						if(miss_flag)
							miss="miss";
						if(eviction_flag)
							eviction="eviction";
						
						System.out.println(action + " " + hex_addr + "," + byte_load + " " + miss + " " + eviction + " " + hit);
						writer.println(action + " " + hex_addr + "," + byte_load + " " + miss + " " + eviction + " " + hit);
			
					}
						
					System.out.println();
					timestamp++;
		       } 
		    	
		    }
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		cache.printCache();
		
		printResult(hits, misses, evictions);
		
		writer.close();
		
		System.exit(0);
	}
	
	static void printAddressDetails(String bin_addr, String tag, String index, int tagAsInt, int indexAsInt) {
		
		String details = bin_addr + "\n" + tag + " " + index + "\n" + "tag: " + tagAsInt + " index(set):"+indexAsInt;
		
		writer.println(details);
		System.out.println(details);
	}
	
	static void printResult(int hits, int misses, int evictions) {
		
		String result = "/nhits: " + hits + " misses: " + misses + " evictions: " + evictions;
		
		writer.println(result);
		System.out.println(result);
	}
	
	static void printSettings(int anzahl_index_bits, int anzahl_cache_bloeke, int anzahl_bloeke_pro_satz, int anzahl_block_bits, String tracefile, boolean verbose_mode) {
		
		String parameters = "\nParameters:" +
				   "\nAnzahl Index Bits: " + anzahl_index_bits +
				   "\nAnzahl Cache Blöcke: " + anzahl_cache_bloeke +
				   "\nAnzahl Blöcke pro Satz: "+ anzahl_bloeke_pro_satz +
				   "\nAnzahl Block Bits: "+ anzahl_block_bits +
				   "\nTracefile: "+ tracefile +
				   "\nVerbose Mode: "+ verbose_mode + "\n\n";
		
		writer.println(parameters);
		System.out.println(parameters);
		
	}
	
}
