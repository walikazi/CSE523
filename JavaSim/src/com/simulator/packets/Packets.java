package com.simulator.packets;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Formatter;

import org.apache.log4j.Logger;

import com.simulator.ccn.CCNQueue;
import com.simulator.ccn.CCNRouter;
import com.simulator.controller.SimulationController;
import com.simulator.controller.SimulationTypes;
import com.simulator.topology.Grid;

import arjuna.JavaSim.Simulation.*;

public class Packets implements Cloneable
{
	static final Logger log = Logger.getLogger(Packets.class);
	/**
	 * This is global counter for packetId's. When ever you want to generate a packet ig call the getter of this var.
	 */
	private static int currenPacketId=0;
	/**
	 * Response Time. Gets updated once the packet is processed.
	 *TODO Hasn't implemented this feature yet. future work.
	 */
	private double ResponseTime;
	/**
	 * The time when packet arrived
	 */
	
	private double ArrivalTime;
	/**
	 * Id of this packet instance.
	 */
	private int packetId = 0;
	/**
	 * Source Packet Id only makes sence when the packet is a clone. And this represents the packetid of the source packet.
	 */
	private int sourcePacketId = 0;
	
	/**
	 * Node id of the previous hop from where the packet came.
	 */
	private int prevHop = -1;
	
	/**
	 * The id of the node where Interest Packet or Data Packet were orginated.
	 */
	
	private int originNode = -1;
	/**
	 * Id of the data packet this interest packet is interested in.
	 */
	
	/**
	 * Curnode denotes the Id of the node to which this packet currently belongs to.
	 */
	private int curNode = -1;
	/**
	 * This denotes the dataPacket Interest Packet is looking for and Interest Packet this data packets is looking to satisfy.
	 */
	private int refPacketId=0;
	/*
	 * gives the size of the packet. Set using setters and getters.
	 */
	private int sizeOfPacket = 1;
	
	/**
	 * Type of the packet.
	 */
	private SimulationTypes packetType;
	
	/**
	 * Number of hops 
	 */

	private int noOfHops=0;
	/**
	 * Statstics dump file
	 */
	private static String dataDumpFile="packetsDump.txt";
	/**
	 * Only meaningful for data packets. 0 means belongs to global cache and 1 means belongs to local cache.
	 * TODO as of now adding very naive way of setting local. Please revisit when have time.
	 */
	private boolean local;
	/**
	 * Indicates if the packet is still alive or isdead. This parameter is used to denote the state of the packet before dumping its statistics.
	 *
	 */
	private boolean alive;
	
	/**
	 * Denotes the cause of suppression. Used while dumping the the packet. Its is set by passing the cause of suppression to 
	 * finished method.
	 */
	private SimulationTypes causeOfSupr;
	
	/**
	 * Comma seperated list of nodes traversed by this packet. Only used of debugging purpose.
	 */
	private String pathTravelled;
	/**
	 * 
	 * This is a constructor of a packet. It takes following param and sets them.
	 * @param nodeId If its Interest Packet than this determines the source of
	 * 		   of the Interest Packet. When we are flooding we change the source
	 * 			of the Interest Packet to the node that is flooding.
	 * 		   else if a packet is of type Data Packet then nodeId represents the node which owns this data packet.
	 * @param packettype Type of the packet.
	 * Notes:
	 * When a packet is created 
	 * We get a unique Id from a static packet Id generator and assign it to PacketId. We also assign the same Id of sourceId since we
	 * are creating the packet here.
	 */
	public Packets (Integer nodeId, SimulationTypes packettype,Integer size)
	{
		setPacketId(getCurrenPacketId());
		setSourcePacketId(getPacketId());
		setPacketType(packettype);
		setPrevHop(-1);
		setRefPacketId(-1);
		setOriginNode(nodeId);
		setSizeOfPacket(size);
		setAlive(true);
		setCauseOfSupr(SimulationTypes.SIMULATION_NOT_APPLICABLE);
		log.info("node id = "+nodeId+" packet id ="+ packetId);

		ResponseTime = 0.0;
		ArrivalTime = Scheduler.CurrentTime();
	}
	public Packets(Packets pac)
	{
		
	}
	/**
	 *  Activates packet. It performs necessary action on the packet. Depending on the packet type.
	 *  @author contra
	 *  TODO Also accept a parameter on what to do.
	 */
	public void activate()
	{
		if(SimulationTypes.SIMULATION_PACKETS_INTEREST == getPacketType())
			interestPacketHandler();
		else
			log.info("Not activation method specified or found");

	}
	
	
	private void interestPacketHandler()
	{
		log.info("Handling Interest Packet"+this.toString());
		CCNRouter router = Grid.getRouter(getOriginNode());
		CCNQueue packetsQ = router.getPacketsQ();
		packetsQ.addLast(this); //Note: Router activation is done when we add the packet to the queue by the queue
		//CCNRouter.TotalPackets++;
		//router.Activate();
	}
	
	/**
	 * This function is called when the Packet is about to Die.
	 * Various scenarious when a packet can die are
	 * 1. When there is No entry in pit table for this data packet.
	 * 2. Already served interest packet
	 * 3. Statisfying the interest packet by sending the 
	 *    corresponding data packet.
	 * 4. When there is a hit in forwarding table.
	 * 5. When there is already an entry in PIT table.  
	 */

	public void finished(SimulationTypes cause)
	{
		ResponseTime = Scheduler.CurrentTime() - ArrivalTime;
		setCauseOfSupr(cause);
		setAlive(false);
		dumpStatistics(this);
		log.info("Finished Packetid:"+getPacketId());
		SimulationController.incrementPacketsProcessed();
	}
	
	public synchronized static void dumpStatistics(Packets curPacket)
	{
		try {
			@SuppressWarnings("unused")
			Writer fs = new BufferedWriter(new FileWriter("dump/packetsDump.txt",true));
			StringBuilder str1 = new StringBuilder();
			Formatter str = new Formatter(str1);
			if(SimulationTypes.SIMULATION_PACKETS_DATA == curPacket.getPacketType())
				str.format("d");
			else 
				str.format("i");
			str.format(" TIME:%(,2.4f",SimulationProcess.CurrentTime());
			str.format(" PAC_ID:%2d",curPacket.getPacketId());
			//str.format(" SRCPACK_ID:%2d",curPacket.getSourcePacketId());
			str.format(" ORGIN_NODE:%2d",curPacket.getOriginNode());
			str.format(" CUR_NODE:%2d",curPacket.getCurNode());
			str.format(" REFPAC_ID:%2d",curPacket.getRefPacketId());
			str.format(" PREV_HOP_ID:%2d",curPacket.getPrevHop());
			str.format(" NO_HOPS:%2d",curPacket.getNoOfHops());
			if(curPacket.isLocal())
				str.format(" LOCAL:1");
			else
				str.format(" LOCAL:0");
			str.format(" CAUES:%s",Integer.toString(curPacket.getCauseOfSupr().ordinal()) );
			str.format(" ALIVE:"+ Integer.toBinaryString((curPacket.isAlive())?1:0));
			str.format("\n");
			fs.write(str.toString());
			fs.close();
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
	public Integer getPacketId() {
		return packetId;
	}

	public void setPacketId(Integer packetId) {
		this.packetId = packetId;
	}

	public SimulationTypes getPacketType() {
		return packetType;
	}

	public void setPacketType(SimulationTypes packetType) {
		this.packetType = packetType;
	}
	public Integer getPrevHop() {
		return prevHop;
	}

	public void setPrevHop(Integer sourceNode) {
		this.prevHop = sourceNode;
	}
	/**
	 * Overriding toString method
	 */
	@Override
	public String toString()
	{
		String str = new String();
		str = "Packet{PacketId: "+Integer.toString(packetId)+" PrevHop: "+getPrevHop().toString()+" No. Of hops:"+getNoOfHops()
		       +" CurrentNode"+getCurNode()+" OriginNode:"+getOriginNode()+" dataPacket:"+getRefPacketId()
		+" size:"+getSizeOfPacket()+" PacketType:"+getPacketType().toString()+"}\n";
		return str;
		
	}
	@Override
	public Object clone()
	{
		try
		{
			Packets clonedPacket = (Packets) super.clone();
			//clonedPacket.pathTravelled = new String(this.getPathTravelled());
			return clonedPacket;
		}
		catch(CloneNotSupportedException e)
		{
			throw new Error("Got clone not support Exception in Packets class");
		}
	}
/*
 * Returns the size of packet.
 */
	public Integer getSizeOfPacket() {
		return sizeOfPacket;
	}
	/*
	 * Sets the size of the packet. 
	 */
	public void setSizeOfPacket(Integer sizeOfPacket) {
		this.sizeOfPacket = sizeOfPacket;
	}

	public Integer getRefPacketId() {
		return refPacketId;
	}
	public void setRefPacketId(Integer dataPacketId) {
		this.refPacketId = dataPacketId;
	}
	public static synchronized Integer getCurrenPacketId() {
		return currenPacketId++;
	}
	public static synchronized void setCurrenPacketId(Integer currenPacketId) {
		Packets.currenPacketId = currenPacketId;
	}
	public int getOriginNode() {
		return originNode;
	}
	public void setOriginNode(int originNode) {
		this.originNode = originNode;
	}
	public int getNoOfHops() {
		return noOfHops;
	}
	public void setNoOfHops(int noOfHops) {
		this.noOfHops = noOfHops;
	}
	/**
	 * Increments number of hops by one
	 */
	public void incrHops()
	{
		setNoOfHops(getNoOfHops()+1);
	}
	public static String getDataDumpFile() {
		return dataDumpFile;
	}
	public static void setDataDumpFile(String dataDumpFile) {
		Packets.dataDumpFile = dataDumpFile;
	}
	public int getCurNode() {
		return curNode;
	}
	public void setCurNode(int curNode) {
		this.curNode = curNode;
	}
	public boolean isLocal() {
		return local;
	}
	public void setLocality(boolean locality) {
		this.local = locality;
	}
	public boolean isAlive() {
		return alive;
	}
	public void setAlive(boolean alive) {
		this.alive = alive;
	}
	public SimulationTypes getCauseOfSupr() {
		return causeOfSupr;
	}
	public void setCauseOfSupr(SimulationTypes causeOfSupr) {
		this.causeOfSupr = causeOfSupr;
	}
	public int getSourcePacketId() {
		return sourcePacketId;
	}
	public void setSourcePacketId(int sourcePacketId) {
		this.sourcePacketId = sourcePacketId;
	}
	public String getPathTravelled() {
		return pathTravelled;
	}
	public void setPathTravelled(int node) {
		pathTravelled.concat(","+Integer.toString(node, 10));
	}
	
};
