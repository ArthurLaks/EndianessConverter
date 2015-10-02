package converter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.EOFException;
import java.io.PrintStream;
import java.io.FileNotFoundException;


/**
 * File:  Assign01.java
 * Assignment: CMSC 331 Project 1
 * Name: Arthur Laks
 * Data: 2015-02-20
 * 
 * This file contains the driver class.
 * It consists of 5 methods:
 * 	main:
 * 		The main method determines which method to call and the arguments to pass, based on 
 * which options the user choose.
 * 
 * 	convertFile:
 * 		The convertFile method takes two filenames.  It opens a file in big endian, converts it
 * little endian, and writes it to the other file.  It returns an array of the number of 
 * messages of each type that it encountered.
 * 
 * 	toCSV:
 * 		The toCSV method takes two filenames.  It opens a file in big endian, converts it to CSV
 *  and writes it to the other file.
 *  
 *  processMessage:
 *  	The processMessage method takes an inputStream, an outputStream, and an array containing
 *  the length of each component of the message, reads each component, switches the endianess,
 *  and writes the resulting message to the outputStream.
 *  
 *  convertEndianess:
 *  	The convertEndianess method takes an array of bytes, reverses it, and returns the 
 *  resulting array.  It does not modify its argument.
 */
public class Assign01 {
	
	static final byte SETUP_MESSAGE = 100;
	static final byte DATA_MESSAGE = 112;
	static final byte STATUS_MESSAGE = 120;
	
	//The following fields consist of the number of bytes in each type of message.
	static final int[] SETUP_MESSAGE_COMPONENTS = {4,8,4,4};
	static final int[] DATA_MESSAGE_COMPONENTS = {4,8,4,4};
	static final int[] STATUS_MESSAGE_COMPONENTS = {4,8,2,2,4};
	
	public static void main(String[] args) {
		//If there were too many or too few command prompt arguments
		if(args.length < 2 || args.length > 3){
			System.out.println("Wrong number of parameters.  Two or three are expected.");
			//Return an error code of 1.
			System.exit(1);
		}
		try{
			//Call the appropriate method based on which option was chosen
			switch(args[0]){
			case "-csv":
				toCSV(args[1],args[2]);
				break;

			case "-count":
				//convertFile returns an array of the frequencies of each type of message.  The 
				//first element in the array is the number of setup messages, the second is the
				//number of startup messages and the third one in the number of status messages.
				int[] messageCounts = convertFile(args[1],args[2]);
				System.out.println("Here is the number of messages of each type: ");
				System.out.printf("Setup message: %d\n", messageCounts[0]);
				System.out.printf("Data Messages: %d\n", messageCounts[1]);
				System.out.printf("Status Messages: %d\n",messageCounts[2]);
				break;

			default:
				//If only two arguments were given, interpret them as the file names.  If three
				//were given then one of the arguments is invalid.
				if(args.length == 2)
					convertFile(args[0],args[1]);
				else{
					System.err.printf("%s is not a valid option.",args[0]);
					System.exit(1);
				}
			}
		}catch(FileNotFoundException e){
			//If the file was not found, print an error message and quit. 
			System.err.println("File not found.  ");
			System.exit(1);
		}
	}

	/**
	 * This method reads messages from a binary file in big endian, converts them to little endian,
	 * and writes the resulting messages to another binary file.  Unrecognized messages are 
	 * skipped. 
	 * @param inputFileName The name of the big endian file to read from.
	 * @param outputFileName The name of the big endian file to write to.
	 * @return A array consisting of the number of setup messages, data messages, and status
	 * messages found, respectively.
	 */
	private static int[] convertFile(String inputFileName,String outputFileName)
	throws FileNotFoundException{
		//The number of occurrences of each type of message. 
		int setupMessages = 0, statusMessages = 0, dataMessages = 0;
		
		//Java 1.7 allows declaring classes that implement the Closeable interface as part of a
		//try block.  Those objects are closed at the end of the block.
		try(DataInputStream inStream = new DataInputStream(new FileInputStream(inputFileName));
				DataOutputStream outStream = new DataOutputStream(new FileOutputStream(outputFileName));
				){
	
			//While there are bytes left in the file.
			while(true){
			    byte id;
			    try{
				id = inStream.readByte();
			    }catch(EOFException e){
				break;
			    }
				
				inStream.skip(1);    //Skip the spare byte.
				
				//If the type of the message is unknown then skip the rest of the message and
				//do not write anything to the output file.
				if(id != SETUP_MESSAGE && id != STATUS_MESSAGE && id != DATA_MESSAGE){
					//Read the length of the message from the file.  Assume that the input file
					//is big endian.
					short toSkip = inStream.readShort();
					toSkip -= 4;	//Subtract the four bytes that were already read from the
					//message (the id, the spare, and the two bytes of the length).
					inStream.skip(toSkip);
					continue;	//Skip the rest of the loop.
				}
				
				//Read the length of the message as a byte array.
				byte[] length = new byte[2];
				inStream.read(length,0,2);    
				
				//Write the id, the spare byte, and the length of the message, converted to little
				//endian.
				outStream.write(id);
				outStream.writeByte(0);  
				outStream.write(convertEndianess(length));
				
				switch(id){
				case SETUP_MESSAGE:
				    ++setupMessages;
				    //The processMessage method reads each component of the message from the 
				    //input stream, converts its endianess, and writes it to the output stream.
					processMessage(inStream,outStream,SETUP_MESSAGE_COMPONENTS);
					//The SETUP_MESSAGE_COMPONENTS array does not include the name of the operator,
					//so it is the only part of the message left.  Read it from the input stream
					//and write it to the output stream.
					byte[] operator = new byte[32];
					inStream.read(operator,0,32);
					outStream.write(operator);
					break;
					
				case STATUS_MESSAGE:
				    ++statusMessages;
					processMessage(inStream,outStream,STATUS_MESSAGE_COMPONENTS);
					break;
					
				case DATA_MESSAGE:
				    ++dataMessages;
					processMessage(inStream,outStream,DATA_MESSAGE_COMPONENTS);
					break;

				}
			}


						//If the exception is a FileNotFoundException then re-throw it to the main method so
			//it can print an error message.  Otherwise, it is probably and EndOfFileException,
			//so exit normally.
		}catch(FileNotFoundException e){
			throw e;
		}catch(IOException e){
		
		}
		//Combine the numbers of each type of message into an array and return the array.
		int[] retval = {setupMessages,dataMessages,statusMessages};
		return retval;
		
	}
	
	/**
	 * This method opens the input file, reads the messages from it, and writes them to the 
	 * output file in CSV format.
	 * @param inputFileName The name of the file to read.
	 * @param outputFileName The name of the file to write to.
	 */
	private static void toCSV(String inputFileName,String outputFileName) throws FileNotFoundException{
		try(DataInputStream source = new DataInputStream(new FileInputStream(inputFileName));
				PrintStream destination = new PrintStream(outputFileName);){
			
			//While there are still bytes left in the file.
			while(source.available() > 0){
				byte id = source.readByte();
				source.skip(1);	//Skip the spare byte.
				
				//If the message is not from one of the recognized types.
				if(id != SETUP_MESSAGE && id != DATA_MESSAGE && id != STATUS_MESSAGE){
					//Read the length of the message, and skip that number of bytes (minus the four
					//byte of the message that were already read).
					short messageLength = source.readShort();
					source.skip(messageLength - 4);
					//Skip the rest of the loop.
					//will be read.
					continue;
				}
				
				destination.printf("%d,%d,",id,source.readShort());		//Print the id and the message length.
				switch(id){
				case SETUP_MESSAGE:
					//Read the seq_num, start_time, latitude and longitude from the source file
					//and print them to the output file.
					destination.printf("%d,%d,%f,%f,",source.readInt(),source.readLong(),source.readFloat(),source.readFloat());
					
					//Read the operator name.
					byte[] operator = new byte[32];
					source.read(operator,0,32);
					//Write the operator name.
					destination.write(operator);
					//Insert a newline to mark the end of the message.
					destination.println();
					break;
					
				case DATA_MESSAGE:
					//Read the seq_num, start_time, speed, and samples from the source file and write 
					//them to the output file.
					destination.printf("%d,%d,%f,%d\n",source.readInt(),source.readLong(),
							source.readFloat(),source.readInt());
					break;
					
				case STATUS_MESSAGE:
					//Read the seq_num, start_time, error_code, component, and version from the input
					//file and write them to the output file.
					destination.printf("%d,%d,%d,%d,%d\n",source.readInt(),source.readLong(),
							source.readShort(),source.readShort(),source.readInt());
					break;
					
				}	
			}
		}catch(FileNotFoundException e){
			throw e;
		}catch(IOException e){

		}
	}

/**
 * This method reads components of a message from a file, swaps their endianesses, and writes
 * the to another file.
 * @param source The stream to read from.
 * @param destination The stream to write to.
 * @param lengths An array containing the length of each component of the message (in bytes).
 * @throws IOException
 */
	private static void processMessage(DataInputStream source,DataOutputStream destination,int[] lengths)
	throws IOException{
		for(int cLength:lengths){
			byte[] component = new byte[cLength];
			source.read(component);
			destination.write(convertEndianess(component));
		}
	}
	
	/**
	 * Reverses an array of bytes.
	 * @param source The array to reverse.
	 * @return The reversed array.
	 */
	private static byte[] convertEndianess(byte[] source){
		byte retval[] = new byte[source.length];
		for(int counter = 0;counter < source.length;++counter){
			retval[counter] = source[source.length - counter - 1];
		}
		return retval;
	}
	
}
