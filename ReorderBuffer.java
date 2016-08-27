package SimulatorPack;

public class ReorderBuffer {
    int[] data; //entries in the ROB
    int head;
    int tail;
    int size; //maximum size of the ROB
    int total; //total items thus far in the ROB
    
    ReorderBuffer(int x)
    {
        data = new int[x];
        head = 0;
        tail = 0;
        size = x;
        total = 0;
    }
    int getHead() //The instruction at the head
    {
        return data[head];
    }
    int getTail()
    {
        return data[tail];
    }
    void insert(int x) //Insert the item at the tail
    {
        //move the tail forward
        if(tail-1 == size-1 && total < size)
        {
            tail = 0; //start inserting from the beginning
        }
        data[tail] = x;
        total++;
        tail++;
    }
    void commit()
    {
        //Index of the head = last index
        data[head] = -1;
        if(head == size -1)
        {
            head = 0;
        } 
        else
        {
            head++;
        }
        total--;
    }
    boolean isFull()
    {
        if(size == total)
            return true;
        else
            return false;
    }
    boolean isEmpty()
    {
        if(total == 0)
        {
            return true;
        }
        else
            return false;
    }
    void printElements()
    {
        System.out.print("[");
        for(int i = 0; i < data.length; i++)
        {
            System.out.print(data[i]+",");
        }
        System.out.println("]");
        System.out.println("HEAD INDEX: "+head);
        System.out.println("TAIL INDEX: "+tail);
        System.out.println("TOTAL: "+total);
        System.out.println("SIZE:" +size);
        System.out.println("*****************");
    }
    public int[] getElements()
    {
        return data;
    }
}
