package SimulatorPack;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class FixedQueue{
    //Maximum Size of the Queue
    int size;
    int total; //total items added to the queue thus far
    Queue<Integer> data = new LinkedList<Integer>();
    FixedQueue(int x)
    {
        size = x;
        total = 0;
    }
    //INSERT: an item to the queue
    void add(int x)
    {
        data.add(x);
        total++;
    }
    int remove()
    {
        total--;
        return data.poll();
    }
    int see()
    {
        return data.peek();
    }
    int[] getElements()
    {
        int[] output = new int[total];
        int count = 0;
        Iterator it = data.iterator();
        while(it.hasNext())
        {
            output[count] = (int)it.next();
            count++;
        }
        return output;
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
            return true;
        else
            return false;
    }
}

