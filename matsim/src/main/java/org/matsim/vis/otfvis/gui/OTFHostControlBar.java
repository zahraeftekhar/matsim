/* *********************************************************************** *
 * project: org.matsim.*
 * OTFHostControlBar.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.vis.otfvis.gui;

import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.border.Border;

import org.apache.log4j.Logger;
import org.matsim.core.gbl.MatsimResource;
import org.matsim.core.utils.misc.Time;
import org.matsim.vis.otfvis.OTFClientControl;
import org.matsim.vis.otfvis.data.OTFClientQuad;
import org.matsim.vis.otfvis.executables.OTFVisController;
import org.matsim.vis.otfvis.interfaces.OTFDrawer;
import org.matsim.vis.otfvis.interfaces.OTFLiveServerRemote;
import org.matsim.vis.otfvis.interfaces.OTFQuery;
import org.matsim.vis.otfvis.interfaces.OTFServerRemote;
import org.matsim.vis.otfvis.opengl.layer.SimpleStaticNetLayer;


/**
 * This class is most important for the OTFVis client. It serves as the connector to the actual server.
 * Additionally it is responsible for all actions associated with the control bar on top of the OTFVis' screen.
 * Any communication with the server will run through this class.
 * 
 * @author dstrippgen
 *
 */
public class OTFHostControlBar extends JToolBar implements ActionListener, ItemListener {
	
	private static final Logger log = Logger.getLogger(OTFHostControlBar.class);
	
//	private static final String CONNECT = "connect";
	private static final String TO_START = "to_start";
	private static final String PAUSE = "pause";
	private static final String PLAY = "play";
	private static final String STEP_F = "step_f";
	private static final String STEP_FF = "step_ff";
	private static final String STOP = "stop";
	private static final String SET_TIME = "set_time";
	private static final String TOGGLE_SYNCH = "Synch";
	private static final String STEP_BB = "step_bb";
	private static final String STEP_B = "step_b";
	private static final String FULLSCREEN = "fullscreen";

	//protected static int DELAYSIM = 30; // time to wait per simstep while play in millisec

	// -------------------- MEMBER VARIABLES --------------------

	private transient MovieTimer movieTimer = null;
	private JButton playButton;
	private JFormattedTextField timeField;
	private int simTime = 0;
	private boolean synchronizedPlay = true;
//	protected OTFLiveServerRemote liveHost = null;

//	private String address;
//	protected OTFServerRemote host = null;
	protected int controllerStatus = 0;
	
	private final List <OTFHostConnectionManager> hostControls = new ArrayList<OTFHostConnectionManager>();

	private ImageIcon playIcon = null;
	private ImageIcon pauseIcon = null;

	public JFrame frame = null;

	private Rectangle windowBounds = null;

	private final OTFHostConnectionManager hostControl;
	private int gotoTime = 0;
	private int gotoIter = 0;
	private transient OTFAbortGoto progressBar = null;

	// -------------------- CONSTRUCTION --------------------

	public OTFHostControlBar(String address, boolean makeButtons) throws RemoteException, InterruptedException, NotBoundException {
		this.hostControl = new OTFHostConnectionManager(address);
		this.hostControls.add(this.hostControl);
		if(makeButtons){
		  addButtons();
		}
	}
	public OTFHostControlBar(String address) throws RemoteException, InterruptedException, NotBoundException {
		this(address, true);
	}


	


	public void addDrawer(String id, OTFDrawer handler) {
		this.hostControl.getDrawer().put(id, handler);
	}

	public OTFDrawer getHandler(String id) {
		return this.hostControl.getDrawer().get(id);
	}

	public void invalidateDrawers() {
		try {
		for(OTFHostConnectionManager slave : hostControls) {
			for (OTFDrawer handler : slave.getDrawer().values()) {
				handler.invalidate(simTime);
		}
		}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public void redrawDrawers() {
		for(OTFHostConnectionManager slave : hostControls) {
				for (OTFDrawer handler : slave.getDrawer().values()) {
					handler.redraw();
			}
		}		
	}

	public void clearCaches() {
		for(OTFHostConnectionManager slave : hostControls) {
			for (OTFDrawer handler : slave.getDrawer().values()) {
				handler.clearCache();
			}
		}
	}

	private void addButtons() throws RemoteException {
		this.setFloatable(false);

    playIcon = new ImageIcon(MatsimResource.getAsImage("otfvis/buttonPlay.png"), "Play");
    pauseIcon = new ImageIcon(MatsimResource.getAsImage("otfvis/buttonPause.png"), "Pause");

		add(createButton("Restart", TO_START, "buttonRestart", "restart the server/simulation"));
		if (!this.hostControl.getOTFServer().isLive()) {
			add(createButton("<<", STEP_BB, "buttonStepBB", "go several timesteps backwards"));
			add(createButton("<", STEP_B, "buttonStepB", "go one timestep backwards"));
		}
		
		playButton = createButton("PLAY", PLAY, "buttonPlay", "press to play simulation continuously");
		add(playButton);
		add(createButton(">", STEP_F, "buttonStepF", "go one timestep forward"));
		add(createButton(">>", STEP_FF, "buttonStepFF", "go several timesteps forward"));
		MessageFormat format = new MessageFormat("{0,number,00}:{1,number,00}:{2,number,00}");
		if(this.hostControl.getOTFServer().isLive()) {
			 if(controllerStatus != OTFVisController.NOCONTROL) {
					 format = new MessageFormat("{0,number,00}#{0,number,00}:{1,number,00}:{2,number,00}");
			 }
		}
		timeField = new JFormattedTextField(format);
		timeField.setMaximumSize(new Dimension(100,30));
		timeField.setMinimumSize(new Dimension(80,30));
		timeField.setActionCommand(SET_TIME);
		timeField.setHorizontalAlignment(JTextField.CENTER);
		add( timeField );
		timeField.addActionListener( this );

//		add(createButton("fullscreen", FULLSCREEN, "buttonFullscreen", "toggles to fullscreen and back"));

		//add(createButton("--", ZOOM_OUT));
		//add(createButton("+", ZOOM_IN));

		createCheckBoxes();

		add(new JLabel(this.hostControl.getAddress()));
	}

	protected JButton createButton(String altText, String actionCommand, String imageName, final String toolTipText) {
		BorderlessButton button = new BorderlessButton();
		button.putClientProperty("JButton.buttonType","icon");
		button.setActionCommand(actionCommand);
		button.addActionListener(this);
	  button.setToolTipText(toolTipText);

	  if (imageName != null) {
	  	// with image
	  	//Look for the image.
	  	String imgLocation = "otfvis/" + imageName + ".png";
	  	ImageIcon icon =new ImageIcon(MatsimResource.getAsImage(imgLocation), altText);
	  	if(icon.getIconHeight() != -1) button.setIcon(icon);
	  	else button.setText(altText);
	  } else {
	  	// without image
	  	button.setText(altText);
	  }

	  return button;
	}

	public void updateTimeLabel() throws RemoteException {
		simTime = this.hostControl.getOTFServer().getLocalTime();
		if(controllerStatus != OTFVisController.NOCONTROL){
			controllerStatus = ((OTFLiveServerRemote)this.hostControl.getOTFServer()).getControllerStatus();
		}

		switch (OTFVisController.getStatus(controllerStatus)) {
		case OTFVisController.STARTUP:
			timeField.setText(OTFVisController.getIteration(controllerStatus) +"#Preparing...#");
			break;
		case (OTFVisController.RUNNING + OTFVisController.PAUSED):
			if((movieTimer != null) && !synchronizedPlay) stopMovie();
		case OTFVisController.RUNNING:
			timeField.setText(OTFVisController.getIteration(controllerStatus) +"#" +Time.writeTime(simTime));
			break;
		case OTFVisController.REPLANNING:
			timeField.setText(OTFVisController.getIteration(controllerStatus) +"#Replanning...#");
			break;
		case OTFVisController.CANCEL:
			timeField.setText(OTFVisController.getIteration(controllerStatus) +"#Cancelling...#");
			break;

		default:
			timeField.setText(Time.writeTime(simTime));
			break;
		}
	}

	// ---------- IMPLEMENTATION OF ActionListener INTERFACE ----------

	public void stopMovie() {
		if (movieTimer != null) {
			movieTimer.terminate();
			movieTimer.setActive(false);
			movieTimer = null;
			playButton.setIcon(playIcon);
		}
	}

	private void pressed_TO_START() throws IOException {
		stopMovie();
		if(this.hostControl.getOTFServer().isLive()) {
			((OTFLiveServerRemote)this.hostControl.getOTFServer()).requestControllerStatus(OTFVisController.CANCEL);
			requestTimeStep(0, OTFServerRemote.TimePreference.LATER);
			simTime = 0;
			updateTimeLabel();
			repaint();
		} else {
			requestTimeStep(loopStart, OTFServerRemote.TimePreference.LATER);
			System.out.println("To start...");
		}
	}

	private void pressed_PAUSE() throws IOException {
		stopMovie();
		if(hostControl.getOTFServer().isLive()){
			((OTFLiveServerRemote)hostControl.getOTFServer()).pause();
		}
	}

	private void pressed_PLAY() throws IOException {
		if (movieTimer == null) {
 	  	movieTimer = new MovieTimer();
 	 	  movieTimer.start();
 			movieTimer.setActive(true);
 			playButton.setIcon(pauseIcon);
 	  } else {
 	   	pressed_PAUSE();
 	  }
	}

	private void pressed_FULLSCREEN() {
		if (this.frame == null) {
			return;
		}
		GraphicsDevice gd = this.frame.getGraphicsConfiguration().getDevice();
		if (gd.getFullScreenWindow() == null) {
			System.out.println("enter fullscreen");
			this.windowBounds = frame.getBounds();
	  	frame.dispose();
	  	frame.setUndecorated(true);
	  	gd.setFullScreenWindow(frame);
		} else {
			System.out.println("exit fullscreen");
			gd.setFullScreenWindow(null);
			frame.dispose();
			frame.setUndecorated(false);
			frame.setBounds(this.windowBounds);
			frame.setVisible(true);
		}
		float linkWidth = OTFClientControl.getInstance().getOTFVisConfig().getLinkWidth();
		OTFClientControl.getInstance().getOTFVisConfig().setLinkWidth(linkWidth + 0.01f);// forces redraw of network, haven't found a better way to do it. marcel/19apr2009
		SimpleStaticNetLayer.marktex = null;
		redrawDrawers();
	}

	protected boolean requestTimeStep(int newTime, OTFServerRemote.TimePreference prefTime)  throws IOException {
		if (hostControl.getOTFServer().requestNewTime(newTime, prefTime)) {
			simTime = hostControl.getOTFServer().getLocalTime();
			
			for(OTFHostConnectionManager slave : hostControls) {
				if (!slave.equals(this.hostControl))
					slave.getOTFServer().requestNewTime(newTime, prefTime);
			}
			invalidateDrawers();
			return true;
		}
		if (prefTime == OTFServerRemote.TimePreference.EARLIER) {
			System.out.println("No previous timestep found");
		} else {
			System.out.println("No succeeding timestep found");
		}
		return false;
	}

	private void pressed_STEP_F() throws IOException {
		if(movieTimer != null) pressed_PAUSE();
		else requestTimeStep(simTime+1, OTFServerRemote.TimePreference.LATER);
	}

	private void pressed_STEP_FF() throws IOException {
		int bigStep = OTFClientControl.getInstance().getOTFVisConfig().getBigTimeStep();
		if(movieTimer != null) pressed_PAUSE();
		else requestTimeStep(simTime+bigStep, OTFServerRemote.TimePreference.LATER);
	}

	private void pressed_STEP_B() throws IOException {
		requestTimeStep(simTime-1, OTFServerRemote.TimePreference.EARLIER);
	}

	private void pressed_STEP_BB() throws IOException {
		int bigStep = OTFClientControl.getInstance().getOTFVisConfig().getBigTimeStep();
		requestTimeStep(simTime-bigStep, OTFServerRemote.TimePreference.EARLIER);
}

	private void pressed_STOP() throws IOException {
		pressed_PAUSE();
	}


	public void gotoTime() {
		boolean restart = (OTFVisController.getIteration(controllerStatus) == gotoIter) && (gotoTime < simTime);
		
		try {
			// in case of live host, additionally request iteration
			if(hostControl.getOTFServer().isLive()) {
				((OTFLiveServerRemote)hostControl.getOTFServer()).requestControllerStatus(gotoIter);
			}
			
			synchronized(hostControl.blockReading) {
			if(restart)requestTimeStep(gotoTime, OTFServerRemote.TimePreference.RESTART);
			else if (!requestTimeStep(gotoTime, OTFServerRemote.TimePreference.EARLIER))
				requestTimeStep(gotoTime, OTFServerRemote.TimePreference.LATER);
			
			if (progressBar != null) progressBar.terminate = true;
			simTime = hostControl.getOTFServer().getLocalTime();
			updateTimeLabel();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setNEWTime(int newTime_s) {
		if (newTime_s == simTime) return;

		stopMovie();
		gotoTime = newTime_s;
		gotoTime();
	}

	private void changed_SET_TIME(ActionEvent event) {
		String newTime = ((JFormattedTextField)event.getSource()).getText();
		int index = newTime.indexOf("#");
		String tmOfDay = newTime.substring(index+1);
		if((index != -1) && (controllerStatus != OTFVisController.NOCONTROL)) {
			gotoIter = Integer.parseInt(newTime.substring(0, index));
		}
		int newTime_s = (int)Time.parseTime(tmOfDay);
		progressBar  = new OTFAbortGoto(hostControl.getOTFServer(), newTime_s, gotoIter);
		progressBar.start();
		
		gotoTime = newTime_s;
		new Thread (){@Override
		public void run() {gotoTime();}}.start();
	}

// deactivated the method below as it just calls the super-method. Marcel, 31oct2008
//	@Override
//	public void paint(Graphics g) {
//		updateTimeLabel();
//		super.paint(g);
//	}

	protected boolean onAction(String command) {
		return false; // return id command was handeled
	}

	public void actionPerformed(ActionEvent event) {
		String command = event.getActionCommand();
		if (!onAction(command)) {
			try {
				if (TO_START.equals(command))
					pressed_TO_START();
				else if (PAUSE.equals(command))
					pressed_PAUSE();
				else if (PLAY.equals(command))
						pressed_PLAY();
				else if (STEP_F.equals(command))
					pressed_STEP_F();
				else if (STEP_FF.equals(command))
						pressed_STEP_FF();
				else if (STEP_B.equals(command))
					pressed_STEP_B();
				else if (STEP_BB.equals(command))
					pressed_STEP_BB();
				else if (STOP.equals(command))
					pressed_STOP();
				else if (FULLSCREEN.equals(command)) {
					pressed_FULLSCREEN();
				} else if (command.equals(SET_TIME))
					changed_SET_TIME(event);
			} catch (IOException e) {
				System.err.println("ControlToolbar encountered problem: " + e);
			}
		}
		try {
			updateTimeLabel();
		} catch (RemoteException e) {
		}

		repaint();

		//networkScrollPane.repaint();
	}

	private void createCheckBoxes() {
		if (hostControl.isLiveHost()) {
			JCheckBox SynchBox = new JCheckBox(TOGGLE_SYNCH);
			SynchBox.setMnemonic(KeyEvent.VK_V);
			SynchBox.setSelected(synchronizedPlay);
			SynchBox.addItemListener(this);
			add(SynchBox);
		}

	}
	public void itemStateChanged(ItemEvent e) {
		JCheckBox source = (JCheckBox)e.getItemSelectable();
		if (source.getText().equals(TOGGLE_SYNCH)) {
			if (movieTimer != null) movieTimer.updateSyncPlay(e.getStateChange() != ItemEvent.DESELECTED);
		}
		repaint();
	}



	private int loopStart = 0;
	private int loopEnd = Integer.MAX_VALUE;

	/**
	 *  sets the loop that the movieplayer should loop
	 * @param min either sec for startloop or -1 for leave unchanged default =0
	 * @param max either sec for endloop or -1 for leave unchanged default = Integer.MAX_VALUE
	 */
	public void setLoopBounds(int min, int max) {
		if(min != -1) loopStart = min;
		if(max != -1) loopEnd = max;
	}

	class MovieTimer extends Thread {
		private boolean isActive = false;
		private boolean terminate = false;

		public MovieTimer() {
			setDaemon(true);
		}

		public synchronized boolean isActive() {
			return isActive;
		}

		private synchronized void updateSyncPlay(boolean sync) {
			try {
				if (isActive) {
					if(!hostControl.isLiveHost()) return;
					// this is only calles for Live Servers!
					// before we sent the host sleeping, we make sure, there i no pending getTimeStep waiting for results
					if (sync)((OTFLiveServerRemote)hostControl.getOTFServer()).pause();
					else ((OTFLiveServerRemote)hostControl.getOTFServer()).play();
				}
				simTime = hostControl.getOTFServer().getLocalTime();
				synchronizedPlay = sync; 
			} catch (RemoteException e) {
				e.printStackTrace();
			}

		}

		public synchronized void setActive(boolean isActive) {
			this.isActive = isActive;

			updateSyncPlay(synchronizedPlay);
		}

		public synchronized void terminate() {
			this.terminate = true;
		}

		@Override
		public void run() {
			int delay = 30;

			int actTime = 0;

			while (!terminate) {
				try {
				  delay = OTFClientControl.getInstance().getOTFVisConfig().getDelay_ms();
					sleep(delay);
					synchronized(hostControl.blockReading) {
						if (isActive && synchronizedPlay &&
							((simTime >= loopEnd) || !hostControl.getOTFServer().requestNewTime(simTime+1, OTFServerRemote.TimePreference.LATER))) {
							hostControl.getOTFServer().requestNewTime(loopStart, OTFServerRemote.TimePreference.LATER);
						}

						actTime = simTime;
						simTime = hostControl.getOTFServer().getLocalTime();
						for(OTFHostConnectionManager slave : hostControls) {
							if (!slave.equals(hostControl))
								slave.getOTFServer().requestNewTime(simTime, OTFServerRemote.TimePreference.LATER);
						}
						
						updateTimeLabel();
						if (simTime != actTime) {
							repaint();
							if (isActive)  {
								invalidateDrawers();
							}
						}
					}
					//simTime = actTime;
				} catch (RemoteException e) {
					stopMovie();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}


	// consolidate this with the OTFQuadClient Query method , there should only be ONE way to send queries,
	// apparently queries are not dependent on a certain view right now, so it should be host.doQuery

	public OTFQuery doQuery(OTFQuery query) {
		OTFClientQuad quad = this.getOTFHostControl().getQuads().values().iterator().next();
		return quad.doQuery(query);
	}

	public void finishedInitialisition() {
		this.hostControl.finishedInitialisition();
	}


	private static class BorderlessButton extends JButton {
		public BorderlessButton() {
			super();
			super.setBorder(null);
		}
		@Override
		public void setBorder(final Border border) {
			// ignore border setting to overwrite specific look&feel
		}
	}

	public void addSlave(OTFHostConnectionManager slave) {
		this.hostControls.add(slave);
	}
	
	/**
	 * Method should be removed again when we once finish the refactoring
	 */
	public OTFHostConnectionManager getOTFHostControl(){
		return this.hostControl;
	}
	
}
