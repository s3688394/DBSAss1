import java.io.*;
import java.util.Arrays;

import dbLoadLib.*;

public class dbload {
	// Used for parsing arguments into program
	private static final int EXPECTED_ARGS = 3;
	private static final int DEFAULT_ARGS = 1;
	private static final int PAGE_SIZE_INDEX = 1;
	private static final int INPUT_FILE_INDEX = 2;
	private static final int DEFAULT_PAGE_SIZE = 1024;
	
	// These constants be made to be dynamic later on if needed
	private static int maxPageSize = DEFAULT_PAGE_SIZE;
	private static boolean headerPresent = true;
	private static int numPages = 0;
	private static int[] heapDataTypes = new int[]{LineProcess.STR_TYPE, LineProcess.STR_TYPE,
			  LineProcess.INT_TYPE, LineProcess.STR_TYPE, LineProcess.STR_TYPE,
			  LineProcess.STR_TYPE, LineProcess.INT_TYPE, LineProcess.STR_TYPE,
			  LineProcess.STR_TYPE, LineProcess.STR_TYPE, LineProcess.INT_TYPE,
			  LineProcess.STR_TYPE};
	
	public static void main(String[] args) {
		String inputFile = null;
		String outputFile = null;
		DataOutputStream output = null;
		BufferedReader input;
		
		long beginTime;
		long endTime;
		int recordsRead = 0;
		
		// Parse arguments
		if(args.length == EXPECTED_ARGS) {
			if(args[0].equals("-p")) {
				try {
					maxPageSize = Integer.parseInt(args[PAGE_SIZE_INDEX]);
				} catch (NumberFormatException e) {
					System.err.println("Invalid page size!");
					System.exit(1);
				}
				inputFile = args[INPUT_FILE_INDEX];
				outputFile = "heap." + maxPageSize;
			} else {
				System.err.println("Invalid parameters. Please use the following:");
				System.err.println("java dbload -p pagesize datafile");
				System.exit(1);
			}
		} else if(args.length == DEFAULT_ARGS) {
			inputFile = args[0];
			outputFile = "heap." + maxPageSize;
		} else {
			System.err.println("Invalid parameters. Please use the following:");
			System.err.println("java dbload -p pagesize datafile");
			System.exit(1);
		}
		
		System.err.println("--------------------------------------------------------------------------------");
		System.err.println("INPUTS\n");
		System.err.println("Page size: " + maxPageSize + " Bytes");
		System.err.println("Input file: " + inputFile);
		System.err.println("--------------------------------------------------------------------------------");
		
		try {
			input = new BufferedReader(new FileReader(inputFile));
			
			// Remove Header
			if(headerPresent) {
				input.readLine();
			}
			
			// Process file
			beginTime = System.nanoTime();
			output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)));
			recordsRead = loadDB(input, output);
			output.close();
			endTime = System.nanoTime();
			input.close();
			
			System.err.println("--------------------------------------------------------------------------------");
			System.err.println("SUMMARY STATS\n");
			System.out.printf("Total Time Taken: %d ms\n", (endTime - beginTime) / 1000000);
			System.err.println("Total records read: " + recordsRead);
			System.err.println("Total pages used: " + numPages);
			System.err.println("--------------------------------------------------------------------------------");
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}

	
	public static int loadDB(BufferedReader input, DataOutputStream output) throws IOException {
		Page page = null;
		int recordsRead = 0;
		int[] offsets;
		Record record = null;
		String[] rawData;
		String[] processedData;
		
		while(LineProcess.hasNextData(input)) {
			// Read new data and get offsets if none remaining from previous iteration.
			rawData = LineProcess.getNextData(input, false);
			processedData = Arrays.copyOfRange(rawData, 1, rawData.length);
			processedData[0] = String.format("%s-%s", rawData[0], rawData[1]);
			
			offsets = LineProcess.calcOffset(processedData, heapDataTypes);
			record = new Record(LineProcess.convertToBinary(processedData, heapDataTypes), heapDataTypes, offsets);
			
			// Create new page and add records to page
			if(page == null) {
				page = new Page(maxPageSize, numPages);
				numPages++;
			}
			
			if(page.addRecord(record)) {
				// Discard processed data
				LineProcess.getNextData(input, true);
				
				recordsRead++;
				record = null;
			} else {
				page.writePage(output);
				page = null;
			}
		}
		
		// Write last page
		if(page != null) {
			page.writePage(output);
		}
		return recordsRead;
	}
}