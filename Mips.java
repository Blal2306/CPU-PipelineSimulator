package SimulatorPack;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;

public class Mips {
    static Queue<Integer> instructionsQ = new LinkedList<Integer>();
    static int[][] instructions = new int[200][13]; //encoded instructions table
    static int iCount = 0; //total number of instructions read
    
    //op code -> string
    static Map<Integer, String> numToInstruction = new HashMap<Integer, String>();
    //string -> op code
    static Map<String, Integer> instructionToNum = new HashMap<String, Integer>();
    
    //rename table
    static int[] renameTable = {-1,-1,-1,-1,-1,-1,-1,-1,-1};
    //allocation list
    static int[] allocationList = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    //renamed
    static int[] renamed = {0,0,0,0,0,0,0,0};
    //register
    static int[] register = {-1,-1,-1,-1,-1,-1,-1,-1};
    //physical register
    static int[] physicalRegister = {-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1};
    //memory
    static int [] memory = new int[10000];
    //========== PIPELINE STAGES ==========//
    static FixedQueue f1 = new FixedQueue(1); //FETCH STAGE # 1
    static FixedQueue f2 = new FixedQueue(1); //FETCH STAGE # 2
    static FixedQueue d1 = new FixedQueue(1); //DECODE STAGE # 1
    static FixedQueue d2 = new FixedQueue(1); //DECODE STAGE # 2
    
    static FixedQueue iq = new FixedQueue(8); //ISSUE QUEUE
    static FixedQueue lsq = new FixedQueue(4);//LOAD STORE QUEUE
    
    static FixedQueue intfu = new FixedQueue(1); //INT FU
    static FixedQueue memfu = new FixedQueue(1); //MEM FU
    static FixedQueue multfu = new FixedQueue(1); //MULT FU
    
    static ReorderBuffer rob = new ReorderBuffer(16); //REORDER BUFFER
    static int pc = 0; //PROGRAM COUNTER
    static int cycles = 0; //CYCLES
    
    public static void main(String[] args) throws IOException
    {
        //initialization
        numToInt();
        intToNum();
        initMemory();
        
        loadInstructions("input 1.txt");
        //========== PROGRAM FLOW ==========//
        int input = 0; 
        while(true)
        {
            //ask the user for the input
            System.out.println("initialize, simulate <n> cycles or display....");
            System.out.print("ENTER COMMAND > ");
            Scanner h = new Scanner(System.in);
            String in = h.nextLine();
            
            String[] in2 = in.split(" ");
            if(in2[0].equals("initialize"))
            {
                flushPipeline();
                cycles = 0;
                pc = 0;
                input = 0;
                //clear registers
                for(int i = 0; i < register.length; i++)
                {
                    register[i] = -1;
                }
                //clear renameTable 
                for(int i = 0; i < renameTable.length; i++)
                {
                    renameTable[i] = -1;
                }
                //clear allocation list
                for(int i = 0; i < allocationList.length; i++)
                {
                    allocationList[i] = 0;
                }
                //clear renamed array
                for(int i = 0; i < renamed.length; i++)
                {
                    renamed[i] = 0;
                }
                //clear physical register value
                for(int i = 0 ; i< physicalRegister.length ; i++)
                {
                    physicalRegister[i] = -1;
                }
            }
            else if(in2[0].equals("display"))
            {
                display();
                System.out.println("CYCLES : "+cycles);
                System.out.println("PC : "+pc);
            }
            else if(in2[0].equals("simulate"))
            {
                input = cycles+Integer.parseInt(in2[1]);
                //========== EXECUTION BEGINS ==========//
                while(cycles < input) 
                {
                    // ***** ROB COMMIT STAGE *****//
                    //rob is not empty , has valid result, and stage > 5 (at least FU stage), has 0 cycles left, and the instruction hasn't finished execution
                    if(!rob.isEmpty() && instructions[rob.getHead()][9] != -1 && instructions[rob.getHead()][1] > 5 && instructions[rob.getHead()][4] == 0 && instructions[rob.getHead()][1] != 0)
                    {
                        //copy the day from the DESTINATION PHYSICAL REGISTER -> DEST ARCH REGISTER
                        int x = renameTable[instructions[rob.getHead()][12]];//get the physical register
                        register[instructions[rob.getHead()][12]] = physicalRegister[x];
                        
                        //if the register has been renamed then deallocated it
                        if(renamed[instructions[rob.getHead()][12]] == 1)
                        {
                            //change the stage of the instruction
                            instructions[rob.getHead()][1] = 0; //the instruction has completed
                            allocationList[x] = 0; //add the physical register to the allocation list
                        }
                        else
                            //The instruction has completed execution
                            instructions[rob.getHead()][1] = 0;
                        
                        //commit the instruction at the head of the rob
                        rob.commit();
                    }
                    //***** FREE THE FU'S IF THE INSTRUCTION IN HAS COMPLETED *****//
                    if(!intfu.isEmpty() && instructions[intfu.see()][4] == 0)
                    {
                        intfu.remove();
                    }
                    if(!multfu.isEmpty() && instructions[multfu.see()][4] == 0)
                    {
                        multfu.remove();
                    }
                    if(!memfu.isEmpty() && instructions[memfu.see()][4] == 0)
                    {
                        memfu.remove();
                    }
                    //========== LOGIC FOR ALL THE FUNCTION UNITS ==========//
                    //***** INT FU *****//
                    //if it is empty , IQ is not empty, the instruction at the top is ...
                    if(intfu.isEmpty() && !iq.isEmpty() && (instructions[iq.see()][2] == 1 || 
                                                            instructions[iq.see()][2] == 2 ||
                                                            instructions[iq.see()][2] == 3 ||
                                                            instructions[iq.see()][2] == 5 ||
                                                            instructions[iq.see()][2] == 6 ||
                                                            instructions[iq.see()][2] == 7 ||
                                                            instructions[iq.see()][2] == 11||
                                                            instructions[iq.see()][2] == 12||
                                                            instructions[iq.see()][2] == 13||
                                                            instructions[iq.see()][2] == 14||
                                                            instructions[iq.see()][2] == 15||
                                                            instructions[iq.see()][2] == 16))
                    {
                        if(instructions[iq.see()][2] != 11 && instructions[iq.see()][2] != 12 && instructions[iq.see()][2] != 13 && instructions[iq.see()][2] != 14)
                        {
   
                            //get the destination physical register
                            int x = renameTable[instructions[iq.see()][12]];
                            //copy the result into the physical register
                            physicalRegister[x] = instructions[iq.see()][9];
                        }
                        
                        //subtract one cycle
                        instructions[iq.see()][4] = 0;
                        
                        //change the stage 
                        instructions[iq.see()][1] = 6;
                        
                        //put the instructions in the int fu
                        int t = iq.remove();
                        intfu.add(t);
                        exInt(t);
                        
                    }
                    
                    // ***** MULT FU *****//
                    if(multfu.isEmpty() && !iq.isEmpty() && instructions[iq.see()][2] == 4)
                    {
                        //subtract one cycle
                        instructions[iq.see()][4] = instructions[iq.see()][4] -1;
                        
                        //change the stage for the instruction
                        instructions[iq.see()][1] = 6;
                        
                        //insert the instruction to the fu
                        int x = iq.remove();
                        exMult(x); //calculate the result
                        multfu.add(x);
                    }
                    // cycle 2
                    else if(!multfu.isEmpty() && instructions[multfu.see()][4] == 2)
                    {
                        //subtract one cycle
                        instructions[multfu.see()][4] = instructions[multfu.see()][4] -1;
                    }
                    //cycle 3
                    else if(!multfu.isEmpty() && instructions[multfu.see()][4] == 1)
                    {
                        //subtract one cycle
                        instructions[multfu.see()][4] = instructions[multfu.see()][4] -1;
                        
                        //copy the result in the destination physical register
                        int x = renameTable[instructions[multfu.see()][12]];
                        physicalRegister[x] = instructions[multfu.see()][9];
                    }
                    
                    // ***** MEM FU *****//
                    //STAGE 3
                    if(memfu.isEmpty() && !lsq.isEmpty())
                    {
                        //substract one cycle for the 
                        instructions[lsq.see()][4] = instructions[lsq.see()][4] -1;
                        
                        //change the stage 
                        instructions[lsq.see()][1] = 6;
                        
                        //insert the instruction in the fu
                        int x = lsq.remove();
                        memfu.add(x);
                        exMem(x);//execute
                    }
                    //STAGE 2
                    else if(!memfu.isEmpty() && instructions[memfu.see()][4] == 2){
                        // subtract one cycle
                        instructions[memfu.see()][4] = instructions[memfu.see()][4] -1;
                    }
                    //STAGE 1
                    else if(!memfu.isEmpty() && instructions[memfu.see()][4] == 1)
                    {
                        //subtract one cycle
                        instructions[memfu.see()][4] = instructions[memfu.see()][4] -1;
                        //copy the result to the destination
                        //if the instruction is of type 9 or 10 (STORE)
                        if(instructions[memfu.see()][2] == 9 || instructions[memfu.see()][2] == 10)
                        {
                            int x = renameTable[instructions[memfu.see()][12]];
                            if(instructions[memfu.see()][9] >= 10000 || instructions[memfu.see()][9] < 0)
                            {
                                System.out.println("invalid memory location access ...........");
                                System.exit(0);
                            }
                            else
                            {
                                System.out.println("updating memory location .....");
                                memory[instructions[memfu.see()][9]] = register[instructions[x][12]];
                                //if the register has renamed deallocate the physical register
                                if(renamed[instructions[memfu.see()][12]] == 1)
                                {
                                    allocationList[x] = 0;
                                    //update the stage info
                                    instructions[memfu.see()][1] = 0;
                                }
                                else
                                {
                                    instructions[memfu.see()][1] = 0;
                                }
                            }
                        }
                        else //LOAD INSTRUCTION
                        {
                            //put the value in the destination
                            //what is my destination physical register
                            int x = renameTable[instructions[memfu.see()][12]];
                            System.out.println("physical register "+x+" updated with value "+memory[instructions[memfu.see()][9]]+" from memory location "+instructions[memfu.see()][9]+" .......");
                            System.out.println();
                            //copy the value to the memory
                            physicalRegister[x] = memory[instructions[memfu.see()][9]];
                            //deallocate
                            //if the register has been renamed then deallocated it
                            if(renamed[instructions[memfu.see()][12]] == 1)
                            {
                                //change the stage of the instruction
                                instructions[memfu.see()][1] = 0;
                                allocationList[x] = 0;
                            }
                            else
                                instructions[memfu.see()][1] = 7;
                        }
                    }
                    // ========== IQ LOGIC ==========//
                    if(!iq.isFull() && !d2.isEmpty() && (instructions[d2.see()][2] == 1 ||
                                                         instructions[d2.see()][2] == 2 ||
                                                         instructions[d2.see()][2] == 3 ||
                                                         instructions[d2.see()][2] == 5 ||
                                                         instructions[d2.see()][2] == 6 ||
                                                         instructions[d2.see()][2] == 7 ||
                                                         instructions[d2.see()][2] == 11||
                                                         instructions[d2.see()][2] == 12||
                                                         instructions[d2.see()][2] == 13||
                                                         instructions[d2.see()][2] == 14||
                                                         instructions[d2.see()][2] == 15||
                                                         instructions[d2.see()][2] == 16||
                                                         instructions[d2.see()][2] == 4))
                    {
                        //update the stage info
                        instructions[d2.see()][1] = 5;
                        //insert the instruction
                        int x = d2.remove();
                        iq.add(x);
                        if(instructions[x][2] != 11 && instructions[x][2] != 12 && instructions[x][2] != 13 && instructions[x][2] != 14)
                        {
                            rob.insert(x);
                        }
                    }
                    
                    // ========== LSQ ==========//
                    if(!lsq.isFull() && !d2.isEmpty() && (instructions[d2.see()][2] == 8 ||
                                                          instructions[d2.see()][2] == 9 ||
                                                          instructions[d2.see()][2] == 10))
                    {
                        //update the stage info
                        instructions[d2.see()][1] = 5;
                        //inser the instruction to the lsq
                        int x = d2.remove();
                        lsq.add(x);
                        if(instructions[x][2] == 8)
                        {
                            rob.insert(x);
                        }
                        
                    }
                    
                    // ***** D2 LOGIC *****//
                    if(d2.isEmpty() && !d1.isEmpty() && havePhysicalRegisters())
                    {
                        int x = getFreePhysicalReg();
                        if(x != -1 && instructions[d1.see()][2] != 15 && instructions[d1.see()][2] != 12)
                        {
                            //allocate the physical register
                            renameTable[instructions[d1.see()][12]] = x;
                            
                            //mark it the allocation list
                            allocationList[x] = 1;
                            
                            //update the stage information
                            instructions[d1.see()][1] = 4;
                            
                            //move the instruction here
                            d2.add(d1.remove());
                        }
                        else if(instructions[d1.see()][2] == 15)
                        {
                            System.out.println("halt encountered ...........");
                            System.exit(0);
                        }
                        else if(instructions[d1.see()][2] == 11 || instructions[d1.see()][2] == 12 || instructions[d1.see()][2] == 13 || instructions[d1.see()][2] == 14)
                        {
                            d2.add(d1.remove());
                        }
                        else
                        {
                            System.out.println("can't allocate physical register .....");
                        }
                    }
                    // ***** D1 LOGIC *****//
                    if(d1.isEmpty() && !f2.isEmpty() && !hasDep(f2.see()))
                    {
                        //if the instruction has source read it
                        //READ SOURCE 1
                        if(instructions[f2.see()][2] == 11 || instructions[f2.see()][2] == 12)
                        {
                            instructions[f2.see()][7] = instructions[f2.see()][10];
                        }
                        else if(instructions[f2.see()][5] != -1) //has a physical reg read it
                        {
                            instructions[f2.see()][7] = physicalRegister[instructions[f2.see()][5]];
                        }
                        else if(instructions[f2.see()][5] == -1 && instructions[f2.see()][10] != -1)
                        {
                            instructions[f2.see()][7] = register[instructions[f2.see()][10]];
                        }
                        
                        //READ SOURCE 2
                        if(instructions[f2.see()][2] == 8 || 
                           instructions[f2.see()][2] == 9 ||
                           instructions[f2.see()][2] == 13||
                           instructions[f2.see()][2] == 14)
                        {
                            instructions[f2.see()][8] = instructions[f2.see()][11];
                        }
                        else if(instructions[f2.see()][6] != -1) //it has phy reg
                        {
                            instructions[f2.see()][8] = physicalRegister[instructions[f2.see()][6]];
                        }
                        else if(instructions[f2.see()][6] == -1 && instructions[f2.see()][11] != -1)
                        {
                            instructions[f2.see()][8] = register[instructions[f2.see()][11]];
                        }
                        
                        //update the stage info
                        instructions[f2.see()][1] = 3;
                        
                        //insert the instruction
                        d1.add(f2.remove());
                    }
                    
                    // ***** F2 LOGIC *****//
                    if(f2.isEmpty() && !f1.isEmpty())
                    {
                        //get the stand in registers
                        //SOURCE 1
                        if((instructions[f1.see()][2]!=11 && instructions[f1.see()][2]!=12) &&instructions[f1.see()][10] != -1)// has a valid source
                        {
                            if( renameTable[instructions[f1.see()][10]] != -1) //there is a stand in
                            {
                                instructions[f1.see()][5] = renameTable[instructions[f1.see()][10]];
                            }
                        }
                        //source 2
                        if(instructions[f1.see()][11] != -1) //has a valid source 2
                        {
                            if(renameTable[instructions[f1.see()][11]] != -1)
                            {
                                instructions[f1.see()][6] = renameTable[instructions[f1.see()][11]];
                            }
                        }
                        
                        //update the state info
                        instructions[f1.see()][1] = 2;
                        //insert the instruction
                        f2.add(f1.remove());
                    }
                    // ***** F1 LOGIC *****//
                    if(f1.isEmpty() && pc < iCount)
                    {
                        instructions[pc][1] = 1;
                        f1.add(pc);
                        pc++;
                    }
                    cycles++;
                    printInstructions();
                    display();
                }
                
            }
            else
            {
                System.exit(0);
            }
            
        }
    }
    public static void loadInstructions(String file) throws FileNotFoundException, IOException
    {
        String inputFile = file;
        String line = null;
        FileReader fileReader = new FileReader(inputFile);
        BufferedReader bufferedreader = new BufferedReader(fileReader);
        int pc = 0;
        while((line = bufferedreader.readLine()) != null)
        {
            int temp[] = new int[13]; //temporary holder for the parsed instruction
            String temp1[] = line.split(" ");
            
            //TYPE 10 - STORE 
            //EX: STORE _ _ R5
            if(temp1[0].equals("STORE") && (temp1[3].charAt(0) == 'R'))
            {
                temp[0] = pc; //position of the instruction
                temp[1] = 0; //stage of execution
                temp[2] = 10;
                temp[3] = 3; //Memory FU
                temp[4] = 3; //Cycles (1 cycle in each memory stage)
                temp[5] = -1; //read values from the ARC Register
                temp[6] = -1;
                temp[7] = -1; //data from source 1
                temp[8] = -1; //data from source 2
                temp[9] = -1; //Calculated Result
                temp[10] = Integer.parseInt(temp1[2].substring(1)); //source register 1
                temp[11] = Integer.parseInt(temp1[3].substring(1)); //source register 2
                temp[12] = Integer.parseInt(temp1[1].substring(1)); //dest register 
            }
            //TYPE 9 - STORE
            //EX: STORE _ _ 34
            else if(temp1[0].equals("STORE"))
            {
                temp[0] = pc; //position of the instruction
                temp[1] = 0; //stage of execution
                temp[2] = 9;
                temp[3] = 3; //Memory FU
                temp[4] = 3; //Cycles (1 cycle in each memory stage)
                temp[5] = -1; //read values from the ARC Register
                temp[6] = -1;
                temp[7] = -1; //data from source 1
                temp[8] = -1; //data from source 2
                temp[9] = -1; //Calculated Result
                temp[10] = Integer.parseInt(temp1[2].substring(1)); //source register 1
                temp[11] = Integer.parseInt(temp1[3]); //source register 2
                temp[12] = Integer.parseInt(temp1[1].substring(1)); //dest register 
            }
            //TYPE 1 - ADD
            //EX: ADD R1 R2 R3
            else if(temp1[0].equals("ADD"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 1; //type
                temp[3] = 1; //Int FU
                temp[4] = 1; //1 cycle 
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = Integer.parseInt(temp1[2].substring(1)); //src 1
                temp[11] = Integer.parseInt(temp1[3].substring(1)); //src 2
                temp[12] = Integer.parseInt(temp1[1].substring(1)); //dest
            }
            //TYPE 2 - SUB
            //EX: SUB R1 R2 R3
            else if(temp1[0].equals("SUB"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 2; //type
                temp[3] = 1; //Int FU
                temp[4] = 1; //1 cycle 
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = Integer.parseInt(temp1[2].substring(1)); //src 1
                temp[11] = Integer.parseInt(temp1[3].substring(1)); //src 2
                temp[12] = Integer.parseInt(temp1[1].substring(1)); //dest
            }
            //TYPE 4 - MUL
            //EX: MUL R3 R4 R5
            else if(temp1[0].equals("MUL"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 4; //type
                temp[3] = 2; //Mult FU
                temp[4] = 3; //1 cycle 
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = Integer.parseInt(temp1[2].substring(1)); //src 1
                temp[11] = Integer.parseInt(temp1[3].substring(1)); //src 2
                temp[12] = Integer.parseInt(temp1[1].substring(1)); //dest
            }
            //TYPE 5 - AND
            //EX: AND R6 R7
            else if(temp1[0].equals("AND"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 5;
                temp[3] = 1;
                temp[4] = 1;
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = Integer.parseInt(temp1[1].substring(1));
                temp[11] = Integer.parseInt(temp1[2].substring(1));
                temp[12] = Integer.parseInt(temp1[1].substring(1));
            }
            //TYPE 6 - OR
            //EX: OR R6 R7
            else if(temp1[0].equals("OR"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 6;
                temp[3] = 1;
                temp[4] = 1;
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = Integer.parseInt(temp1[1].substring(1));
                temp[11] = Integer.parseInt(temp1[2].substring(1));
                temp[12] = Integer.parseInt(temp1[1].substring(1));
            }
            //TYPE 7 - EX-OR
            //EX: EX-OR R1 R2
            else if(temp1[0].equals("EX-OR"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 7;
                temp[3] = 1;
                temp[4] = 1;
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = Integer.parseInt(temp1[1].substring(1));
                temp[11] = Integer.parseInt(temp1[2].substring(1));
                temp[12] = Integer.parseInt(temp1[1].substring(1));
            }
            //TYPE 3 - MOVC
            //EX: MOVC R0 11
            else if(temp1[0].equals("MOVC"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 3; //MOVC
                temp[3] = 1;
                temp[4] = 1;
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = Integer.parseInt(temp1[2]);
                temp[10] = -1;
                temp[11] = -1;
                temp[12] = Integer.parseInt(temp1[1].substring(1));
            }
            //TYPE 8 - LOAD
            //EX: LOAD R2 R3 24
            else if(temp1[0].equals("LOAD"))
            {
                temp[0] = pc;
                temp[1] = 0; //stage of execution
                temp[2] = 8;
                temp[3] = 3; //Mem Fu
                temp[4] = 3;
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = Integer.parseInt(temp1[2].substring(1));
                temp[11] = Integer.parseInt(temp1[3]);
                temp[12] = Integer.parseInt(temp1[1].substring(1));
            }
            //TYPE 11 - BZ
            //EX: BZ 5
            else if(temp1[0].equals("BZ"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 11;
                temp[3] = 1; //Int FU
                temp[4] = 1;
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = Integer.parseInt(temp1[1]);
                temp[11] = -1;
                temp[12] = -1;
            }
            //TYPE 12 - BNZ
            //EX: BNZ 88
            else if(temp1[0].equals("BNZ"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 12;
                temp[3] = 1; //Int FU
                temp[4] = 1;
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = Integer.parseInt(temp1[1]);
                temp[11] = -1;
                temp[12] = -1;
            }
            //TYPE 13 - JUMP
            //EX: JUMP R3 20009
            //EX: JUMP X 30203
            else if(temp1[0].equals("JUMP"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 13;
                temp[3] = 1;
                temp[4] = 1;
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                if(temp1[1].equals("X"))
                {
                    temp[10] = 8;
                }
                else
                {
                    temp[10] = Integer.parseInt(temp1[1].substring(1));
                }
                temp[11] = Integer.parseInt(temp1[2]);
                temp[12] = -1;
            }
            //TYPE 14 - BAL
            //EX: BAL R7 88
            else if(temp1[0].equals("BAL"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 14;
                temp[3] = 1;
                temp[4] = 1;
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = Integer.parseInt(temp1[1].substring(1));
                temp[11] = Integer.parseInt(temp1[2]);
                temp[12] = -1;
            }
            //TYPE 16 - MOV
            //EX: MOV R1 R5
            else if(temp1[0].equals("MOV"))
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 16;
                temp[3] = 1;
                temp[4] = 1;
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = Integer.parseInt(temp1[2].substring(1));
                temp[11] = -1;
                temp[12] = Integer.parseInt(temp1[1].substring(1));
            }
            //TYPE 15 - HALT
            else
            {
                temp[0] = pc;
                temp[1] = 0;
                temp[2] = 15;
                temp[3] = 1;
                temp[4] = 1;
                temp[5] = -1;
                temp[6] = -1;
                temp[7] = -1;
                temp[8] = -1;
                temp[9] = -1;
                temp[10] = -1;
                temp[11] = -1;
                temp[12] = -1;
            }
            //MAIN INSTRUCTIONS STACK
            instructionsQ.offer(pc);
            pc++;
            //copy everything to the main instructions holder
            for(int i = 0; i < temp.length; i++)
            {
                instructions[iCount][i] = temp[i];
            }
            iCount++; 
        }//END OF WHILE
    }
    public static void printInstructions()
    {
        System.out.print("PC\tSTG\tTYP\tFU\tCYC\tP1"
                + "\tP2\tD1\tD2\tRES\tS1\tS2\tDES");
        System.out.println();
        for(int i = 0; i < iCount; i++)
        {
            for(int j = 0; j < 13; j++)
            {
                if(j == 2)
                {
                    System.out.print(numToInstruction.get(instructions[i][j])+" "+instructions[i][j]+"\t");
                }
                else
                    System.out.print(instructions[i][j]+"\t");
            }
            System.out.println();
        }
        System.out.println("=========================================================="
                          +"=======================================");
    }
    public static void numToInt()
    {
        numToInstruction.put(1, "ADD");
        numToInstruction.put(2, "SUB");
        numToInstruction.put(3, "MOVC");
        numToInstruction.put(4, "MUL");
        numToInstruction.put(5, "AND");
        numToInstruction.put(6, "OR");
        numToInstruction.put(7, "EX-OR");
        numToInstruction.put(8, "LOAD");
        numToInstruction.put(9, "STORE");
        numToInstruction.put(10, "STORE");
        numToInstruction.put(11, "BZ");
        numToInstruction.put(12, "BNZ");
        numToInstruction.put(13, "JUMP");
        numToInstruction.put(14, "BAL");
        numToInstruction.put(15, "HALT");
        numToInstruction.put(16, "MOV");
    }
    public static void intToNum()
    {
        instructionToNum.put("ADD", 1);
        instructionToNum.put("SUB", 2);
        instructionToNum.put("MOVC", 3);
        instructionToNum.put("MUL", 4);
        instructionToNum.put("AND", 5);
        instructionToNum.put("OR", 6);
        instructionToNum.put("EX-OR", 7);
        instructionToNum.put("LOAD", 8);
        instructionToNum.put("STORE", 9);
        instructionToNum.put("STORE", 10);
        instructionToNum.put("BZ", 11);
        instructionToNum.put("BNZ", 12);
        instructionToNum.put("JUMP", 13);
        instructionToNum.put("BAL", 14);
        instructionToNum.put("HALT", 15);
        instructionToNum.put("MOV", 16);
    }
    public static int getFreePhysicalReg()
    {
        for(int i = 0; i < allocationList.length;  i++)
        {
            if(allocationList[i] == 0)
                return i;
        }
        return -1;
    }
    public static boolean havePhysicalRegisters()
    {
        for(int i = 0; i < allocationList.length; i++)
        {
            if(allocationList[i] == 0)
                return true;
        }
        return false;
    }
    public static void display()
    {
        //RENAME TABLE
        System.out.print("RENAME TABLE : [");
        for(int i = 0; i < renameTable.length; i++)
        {
            if(i == renameTable.length -1)
            {
                System.out.print("X"+" = "+renameTable[i]);
            }
            else
                System.out.print("R"+i+" = "+renameTable[i]+", ");
        }
        System.out.println("]");
        
        //ALLOCATION LIST
        System.out.print("ALLOCATION LIST : [");
        for(int i = 0; i < allocationList.length; i++)
        {
            System.out.print("P"+i+" = "+allocationList[i]+", ");
        }
        System.out.println("]");
        
        //RENAMED LIST
        System.out.print("RENAMED : [");
        for(int i = 0; i < renamed.length; i++)
        {
            if(i == renamed.length -1)
            {
                System.out.print("X"+" = "+renamed[i]);
            }
            else
                System.out.print("R"+i+" = "+renamed[i]+", ");
        }
        System.out.println("]");
        
        //REGISTER
        System.out.print("REGISTER : [");
        for(int i = 0; i < register.length; i++)
        {
            if(i == register.length -1)
            {
                System.out.print("X"+" = "+register[i]);
            }
            else
                System.out.print("R"+i+" = "+register[i]+", ");
        }
        System.out.println("]");
        
        //PHYSICAL REGISTER
        System.out.print("PHYSICAL REGISTER : [");
        for(int i = 0; i < physicalRegister.length; i++)
        {
            System.out.print("P"+i+" = "+physicalRegister[i]+", ");
        }
        System.out.println("]");
        System.out.println();
        
        System.out.println("*** PIPELINE STAGES ***");
        //FETCH STAGE 1
        System.out.println("F1 : " + (!f1.isEmpty() ? numToInstruction.get(instructions[f1.see()][2]) : " "));
        //FETCH STAGE 2
        System.out.println("F2 : " + (!f2.isEmpty() ? numToInstruction.get(instructions[f2.see()][2]) : " "));
        //DECODE STAGE 1
        System.out.println("D1 : " + (!d1.isEmpty() ? numToInstruction.get(instructions[d1.see()][2]) : " "));
        //DECODE STAGE 2
        System.out.println("D2 : " + (!d2.isEmpty() ? numToInstruction.get(instructions[d2.see()][2]) : " "));
        
        //ISSUE QUEUE
        System.out.print("IQ : [ ");
        if(!iq.isEmpty())
        {
            int[] temp = iq.getElements();
            for(int i = 0; i < temp.length; i++)
            {
                System.out.print(numToInstruction.get(instructions[temp[i]][2])+", ");
            }
            System.out.println("]");
        }
        else{
            System.out.println(" ]");
        }
        
        //LOAD STORE QUEUE
        System.out.print("LSQ : [ ");
        if(!lsq.isEmpty())
        {
            int[] temp = lsq.getElements();
            for(int i = 0; i < temp.length; i++)
            {
                System.out.print(numToInstruction.get(instructions[temp[i]][2])+", ");
            }
            System.out.println("]");
        }else{
            System.out.println(" ]");
        }
        
        //INT FU
        System.out.println("INT FU : " + (!intfu.isEmpty() ? numToInstruction.get(instructions[intfu.see()][2]) : " "));
        //MULT FU
        System.out.println("MULT FU : " + (!multfu.isEmpty() ? numToInstruction.get(instructions[multfu.see()][2]) : " "));
        
        //MEM FU
        System.out.print("MEM FU (STAGE 1) : ");
        //MEM FU (STAGE 1)
        if(!memfu.isEmpty() && instructions[memfu.see()][4] == 2)
        {
            System.out.println(numToInstruction.get(instructions[memfu.see()][2]));
        }
        else
            System.out.println();
        
        //MEM FU (STAGE 2)
        System.out.print("MEM FU (STAGE 2) : ");
        if(!memfu.isEmpty() && instructions[memfu.see()][4] == 1)
        {
            System.out.println(numToInstruction.get(instructions[memfu.see()][2]));
        }
        else
            System.out.println();
        
        //MEM FU (STAGE 3)
        System.out.print("MEM FU (STAGE 3) : ");
        if(!memfu.isEmpty() && instructions[memfu.see()][4] == 0)
        {
            System.out.println(numToInstruction.get(instructions[memfu.see()][2]));
        }
        else
            System.out.println();
        
        //ROB (What is at the head of the rob
        System.out.print("ROB : [");
        int[] temp = rob.getElements();
        for(int i = 0; i < temp.length; i++)
        {
            if(temp[i] == 0 || temp[i] == -1)
            {
                System.out.print(" ");
            }
            else
                System.out.print(temp[i]+", ");
        }
        System.out.println("]");
        
        //Memory
        
        System.out.println("Memory : ");
        System.out.println("LOC" + "\t"+"VALUE");
        for(int i = 0; i < 100; i++)
        {
            System.out.println(i+"\t"+memory[i]);
        }
        System.out.println();
        System.out.println("=========================================================="
                          +"=======================================");
    }
    public static boolean hasDep(int x)
    {
        boolean s1 = false;
        boolean s2 = false;
        //SOURCE 1
        //does the instruction have a physical register
        if(instructions[x][2] == 11 || instructions[x][2] == 12)
        {
            s1 = false;
        }
        else if(instructions[x][5] != -1) //it has a physical register, check that for dep
        {
            if(physicalRegister[instructions[x][5]] == -1) //there is no data avail
            {
                s1 = true;
            }
        }
        else if(instructions[x][5] == -1 && instructions[x][10] != -1) //have a valid arc reg
        {
            if(register[instructions[x][10]] == -1)
            {
                s1 = true;
            }
        }
        else if(instructions[x][5] == -1 && instructions[x][10] == -1) //does have phy and arc reg
        {
            s1 = false;
        }
        
        //source 2
        if(instructions[x][2] == 8 || instructions[x][2] == 9 || instructions[x][2] == 13 || instructions[x][2] == 14)
        {
            s2 = false;
        }
        else if(instructions[x][6] != -1) //it has a physical register
        {
            if(physicalRegister[instructions[x][6]] == -1)
            {
                s2 = true;
            }
        }
        else if(instructions[x][6] ==-1 && instructions[x][11] != -1) //have arch reg
        {
            if(register[instructions[x][11]] == -1)
            {
                s2 = true;
            }
        }
        else if(instructions[x][6] == -1 && instructions[x][11] == -1)
        {
            s2 = false;
        }
        if(s1 || s2)
        {
            System.out.println("Dependency Detected .................");
            System.out.println();
        }
        
        return (s1 || s2);
    }
    public static void exMult(int x)
    {
        int x1 = instructions[x][7];
        int x2 = instructions[x][8];
        
        instructions[x][9] = x1 * x2;
    }
    public static void exMem(int x)
    {
        //LOAD INSTRUCTION
        if(instructions[x][2] == 8 || instructions[x][2] == 9 || instructions[x][2] == 10)
        {
            //get the data from the two sources
            int x1 = instructions[x][7];
            int x2 = instructions[x][8];
            if((x1+x2) >= 10000 || (x1+x2) < 0)
            {
                System.out.println("invalid memory location access ......");
                System.exit(0);
            }
            else{
                instructions[x][9] = x1 + x2;
                System.out.println("Reading from memory location "+(x1+x2)+" ......");
                System.out.println();
            }
        }
    }
    public static void exInt(int x)
    {
        //ADD INSTRUCTION
        if(instructions[x][2] == 1)
        {
            int x1 = instructions[x][7];
            int x2 = instructions[x][8];
            instructions[x][9] = x1+x2;
        }
        //SUB
        else if(instructions[x][2] == 2)
        {
            int x1 = instructions[x][7];
            int x2 = instructions[x][8];
            instructions[x][9] = x1-x2;
        }
        //AND
        else if(instructions[x][2] == 5)
        {
            int x1 = instructions[x][7];
            int x2 = instructions[x][8];
            instructions[x][9] = x1 & x2;
        }
        //OR
        else if(instructions[x][2] == 6)
        {
            int x1 = instructions[x][7];
            int x2 = instructions[x][8];
            instructions[x][9] = x1 | x2;
        }
        //EX - OR
        else if(instructions[x][2] == 7)
        {
            int x1 = instructions[x][7];
            int x2 = instructions[x][8];
            instructions[x][9] = x1 ^ x2;
        }
        else if(instructions[x][2] == 16)
        {
            instructions[x][9] = instructions[x][7];
        }
        else if(instructions[x][2] == 15)
        {
            System.out.println("Halt encountered .....");
            display();
            System.exit(0);
        }
        //BZ 4
        else if(instructions[x][2] == 11)
        {
            if(register[instructions[x-1][12]] == 0)
            {
                System.out.println("Branching ......");
                pc = pc + instructions[x][7];
                flushPipeline();
            }
        }
        //BNZ 8
        else if(instructions[x][2] == 12)
        {
             if(register[instructions[x-1][12]] == 0)
            {
                System.out.println("Branching ......");
                pc = pc + instructions[x][7];
                flushPipeline();
            }
        }
        //JUMP
        else if(instructions[x][2] == 13)
        {
            System.out.println("Branching ......");
            int y = instructions[x][7] + instructions[x][8] - 20000;
            pc = y;
            flushPipeline();
        }
        //BAL 
        else if(instructions[x][2] == 14)
        {
            System.out.println("Branching ......");
            register[8] = pc;
            pc = instructions[x][7] + instructions[x][8];
            flushPipeline();
        }
   
    }
    public static void initMemory()
    {
        for(int i = 0 ; i < 10000;i++)
        {
            memory[i] = 0;
        }
    }
    public static void flushPipeline()
    {
        //f1
        if(!f1.isEmpty())
            f1.remove();
        //f2
        if(!f2.isEmpty())
            f2.remove();
        //d1
        if(!d1.isEmpty())
            d1.remove();
        //d2
        if(!d2.isEmpty())
            d2.remove();
        
        //iq
        while(!iq.isEmpty())
            iq.remove();
        
        //lsq
        while(!lsq.isEmpty())
            lsq.remove();
        
        //rob
        while(!rob.isEmpty())
        {
            rob.commit();
        }
        
        //int fu
        if(!intfu.isEmpty())
            intfu.remove();
        
        //multfu
        if(!multfu.isEmpty())
            multfu.remove();
        
        //mem fu
        if(!memfu.isEmpty())
            memfu.remove();
        
    }
}
