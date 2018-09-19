/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/

import java.util.Timer;
import java.util.TimerTask;

public class SWP {

/*========================================================================*
 the following are provided, do not change them!!
 *========================================================================*/
   //the following are protocol constants.
   public static final int MAX_SEQ = 7; 
   public static final int NR_BUFS = (MAX_SEQ + 1)/2;

   // the following are protocol variables
   private int oldest_frame = 0;
   private PEvent event = new PEvent();  
   private Packet out_buf[] = new Packet[NR_BUFS];

   //the following are used for simulation purpose only
   private SWE swe = null;
   private String sid = null;  

   //Constructor
   public SWP(SWE sw, String s){
      swe = sw;
      sid = s;
   }

   //the following methods are all protocol related
   private void init(){
      for (int i = 0; i < NR_BUFS; i++){
	   out_buf[i] = new Packet();
      }
   }

   private void wait_for_event(PEvent e){
      swe.wait_for_event(e); //may be blocked
      oldest_frame = e.seq;  //set timeout frame seq
   }

   private void enable_network_layer(int nr_of_bufs) {
   //network layer is permitted to send if credit is available
	swe.grant_credit(nr_of_bufs);
   }

   private void from_network_layer(Packet p) {
      swe.from_network_layer(p);
   }

   private void to_network_layer(Packet packet) {
	swe.to_network_layer(packet);
   }

   private void to_physical_layer(PFrame fm)  {
      System.out.println("SWP: Sending frame: seq = " + fm.seq + 
			    " ack = " + fm.ack + " kind = " + 
			    PFrame.KIND[fm.kind] + " info = " + fm.info.data );
      System.out.flush();
      swe.to_physical_layer(fm);
   }

   private void from_physical_layer(PFrame fm) {
      PFrame fm1 = swe.from_physical_layer(); 
	fm.kind = fm1.kind;
	fm.seq = fm1.seq; 
	fm.ack = fm1.ack;
	fm.info = fm1.info;
   }


/*===========================================================================*
 	implement your Protocol Variables and Methods below: 
 *==========================================================================*/

 // Added variables
 boolean no_nak = true;

 // Added methods

 // increments frame number to the next (bounded by MAX_SEQ)
 public int inc(int next_frame_to_send) {
  return ((next_frame_to_send + 1) % (MAX_SEQ + 1));
}

// checks if a frame number is within the window
public boolean between(int frame_expected, int frame_seq, int too_far){
  return ((frame_expected <= frame_seq)&&(frame_seq < too_far)) 
      || ((too_far < frame_expected)&&(frame_expected<=frame_seq)) 
      || ((frame_seq<too_far)&&(too_far<frame_expected));
}

// sends a frame
public void send_frame(int frame_kind, int frame_num, int frame_expected, Packet[] buffer){
  PFrame frame = new PFrame();
  frame.kind = frame_kind;

  // if data frame, enter buffer into payload
  if (frame.kind == PFrame.DATA){
    frame.info = buffer[frame_num % NR_BUFS];
  }
  frame.seq = frame_num;
  frame.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);

  if (frame_kind == PFrame.NAK){
    no_nak = false;
  }
  to_physical_layer(frame);

  // if data frame, start timer
  if (frame.kind == PFrame.DATA){
    start_timer(frame_num % MAX_SEQ);
  }
  stop_ack_timer();

}

   public void protocol6() {
        int ack_expected = 0;
        int next_frame_to_send = 0;
        int frame_expected = 0;
        int too_far = NR_BUFS;
        PFrame frame = new PFrame();
        Packet in_buffer[] = new Packet[NR_BUFS];
        boolean arrived[] = new boolean[NR_BUFS];

        // initializing the arrived array
        for (int i = 0; i < NR_BUFS; i++){
          arrived[i] = false;
        }

        init();
        enable_network_layer(NR_BUFS);

        while(true) {
          wait_for_event(event);
	        switch(event.type) {
            case (PEvent.NETWORK_LAYER_READY):
              from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);
              send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf);
              inc(next_frame_to_send);
              break; 

            case (PEvent.FRAME_ARRIVAL):
              from_physical_layer(frame);
              if(frame.kind == PFrame.DATA){

                // if frame is not expected and no NAKs exist yet
                if((frame.seq != frame_expected) && no_nak){
                  send_frame(PFrame.NAK, 0, frame_expected, out_buf);
                }
                else{
                  // start waiting for an ack
                  start_ack_timer();
                }

                // if frame sequence is in window and has not arrived before, set arrived as true & put payload into buffer
                if(between(frame_expected, frame.seq, too_far) && (arrived[frame.seq % NR_BUFS] == false)){
                  arrived[frame.seq % NR_BUFS] = true;
                  in_buffer[frame.seq % NR_BUFS] = frame.info;
                  while(arrived[frame_expected % NR_BUFS]){
                    to_network_layer(in_buffer[frame_expected % NR_BUFS]);
                    no_nak = true;
                    arrived[frame_expected % NR_BUFS] = false;
                    frame_expected = inc(frame_expected);
                    too_far = inc(too_far);
                    start_ack_timer();
                  }
                }
              }
              if((frame.kind == PFrame.NAK) && between(ack_expected, (frame.ack + 1) % (MAX_SEQ + 1), next_frame_to_send)){
                send_frame(PFrame.DATA, (frame.ack + 1) % (MAX_SEQ + 1), frame_expected, out_buf);
              }
              while(between(ack_expected, frame.ack, next_frame_to_send)){
                stop_timer(ack_expected % NR_BUFS);
                ack_expected = inc(ack_expected);
              }
              break;	   

            case (PEvent.CKSUM_ERR):
              if(no_nak){
                send_frame(PFrame.NAK, 0, frame_expected, out_buf);
              }
              break;  

            case (PEvent.TIMEOUT): 
              send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf);
              break; 

	          case (PEvent.ACK_TIMEOUT): 
              send_frame(PFrame.ACK, 0, frame_expected, out_buf);
              break; 

            default: 
		          System.out.println("SWP: undefined event type = " 
                                       + event.type); 
		          System.out.flush();
	        }
        }      
    }

 /* Note: when start_timer() and stop_timer() are called, 
    the "seq" parameter must be the sequence number, rather 
    than the index of the timer array, 
    of the frame associated with this timer, 
   */
 
  Timer[] f_timer = new Timer[NR_BUFS];
  Timer ack_timer;


  public class FrameTimeoutTask extends TimerTask {
    int seq;

    public FrameTimeoutTask(int seq) {
        this.seq = seq;
    }

    public void run() {
        swe.generate_timeout_event(seq);
    }
  }


   private void start_timer(int seq) {
    stop_timer(seq);
    //create new timer and new timertask
    f_timer[seq % NR_BUFS] = new Timer();
    //schedule the  task for execution after 200ms
    f_timer[seq % NR_BUFS].schedule(new f_task(seq), 200);
   }

   private void stop_timer(int seq) {
    if (f_timer[seq % NR_BUFS] != null) {
      f_timer[seq % NR_BUFS].cancel();
      f_timer[seq % NR_BUFS] = null;
    }
   }


   private void start_ack_timer( ) {
    stop_ack_timer();

        //starts another timer for sending separate ack
        ack_timer = new Timer();
        ack_timer.schedule(new ack_task(), 50);
   }

   private void stop_ack_timer() {
    if (ack_timer != null) {
      ack_timer.cancel();
      ack_timer = null;
    }
   }

   class ack_task extends TimerTask {

    public void run() {
        //stop timer
        stop_ack_timer();
        swe.generate_acktimeout_event();
    }
}


class f_task extends TimerTask {

  private int seq;

  public f_task(int seq) {
      this.seq = seq;
  }

  public void run() {
      //stops this timer, discarding any scheduled tasks for the current seq
      stop_timer(seq);
      swe.generate_timeout_event(seq);
  }
}


}//End of class

/* Note: In class SWE, the following two public methods are available:
   . generate_acktimeout_event() and
   . generate_timeout_event(seqnr).

   To call these two methods (for implementing timers),
   the "swe" object should be referred as follows:
     swe.generate_acktimeout_event(), or
     swe.generate_timeout_event(seqnr).
*/


