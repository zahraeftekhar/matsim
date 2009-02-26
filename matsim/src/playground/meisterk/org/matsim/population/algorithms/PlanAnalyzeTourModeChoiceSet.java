/* *********************************************************************** *
 * project: org.matsim.*
 * PlanAnalyzeTourModeChoiceSet.java
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

package playground.meisterk.org.matsim.population.algorithms;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;

import org.matsim.basic.v01.BasicPlanImpl.LegIterator;
import org.matsim.interfaces.basic.v01.BasicLeg;
import org.matsim.interfaces.core.v01.Leg;
import org.matsim.interfaces.core.v01.Plan;
import org.matsim.population.algorithms.PlanAlgorithm;
import org.matsim.world.Location;

import playground.meisterk.org.matsim.config.groups.MeisterkConfigGroup;

public class PlanAnalyzeTourModeChoiceSet implements PlanAlgorithm {

	private MeisterkConfigGroup meisterk = new MeisterkConfigGroup();
	
	private EnumSet<BasicLeg.Mode> modeSet = null;

	private ArrayList<BasicLeg.Mode[]> result = null;
	
	public ArrayList<BasicLeg.Mode[]> getResult() {
		return result;
	}

	public EnumSet<BasicLeg.Mode> getModeSet() {
		return modeSet;
	}

	public void setModeSet(EnumSet<BasicLeg.Mode> modeSet) {
		this.modeSet = modeSet;
	}

	public void run(Plan plan) {
		
		// how many mode combinations are possible?
		int numLegs = plan.getActsLegs().size() / 2;
		
		int numCombinations = (int) Math.pow(this.modeSet.size(), numLegs);
//		System.out.println(Integer.toString(numCombinations));
//
//		System.out.println();
		
		this.result = new ArrayList<BasicLeg.Mode[]>();

		for (int numCombination = 0; numCombination < numCombinations; numCombination++) {

			// setup the trackers for all chain-based modes, set all chain-based modes starting at the first location (usually home)
			HashMap<BasicLeg.Mode, Location> modeTracker = new HashMap<BasicLeg.Mode, Location>();
			for (BasicLeg.Mode mode : this.modeSet) {
				if (meisterk.getChainBasedModes().contains(mode)) {
//				if (mode.isChainBased()) {
					modeTracker.put(mode, plan.getFirstActivity().getFacility());
//					System.out.println(mode + " " + modeTracker.get(mode).getId());
				}
			}

//			System.out.println();

			BasicLeg.Mode[] candidate = new BasicLeg.Mode[numLegs]; 

			String modeIndices = Integer.toString(numCombination, this.modeSet.size());
			while (modeIndices.length() < numLegs) {
				modeIndices = "0".concat(modeIndices);
			}
//			System.out.println("Mode indices: " + numCombination + " / " + modeIndices);
			LegIterator legIterator = plan.getIteratorLeg();
			boolean modeChainIsFeasible = true;
			int legNum = 0;
			while (modeChainIsFeasible && legIterator.hasNext()) {

				Leg currentLeg = (Leg) legIterator.next();

				BasicLeg.Mode legMode = (BasicLeg.Mode) this.modeSet.toArray()[Integer.parseInt(modeIndices.substring(legNum, legNum + 1))];
//				System.out.println("Mode test for leg num " + Integer.toString(legNum) + ": " + legMode);
				if (meisterk.getChainBasedModes().contains(legMode)) {
//				if (legMode.isChainBased()) {
					Location currentLocation = modeTracker.get(legMode);
					Location requiredLocation = plan.getPreviousActivity(currentLeg).getFacility();
					if (currentLocation.equals(requiredLocation)) {
						candidate[legNum] = legMode;
						modeTracker.put(legMode, plan.getNextActivity(currentLeg).getFacility());

					} else {
//						System.out.println("Mode chain not feasible. Aborting...");
						modeChainIsFeasible = false;
						// compute number of next candidate for a feasible combination, that is, omit the detected branch of infeasible combinations
						numCombination += ((int) Math.pow(this.modeSet.size(), (numLegs - legNum - 1))) - 1;
					}
					
				} else {
					candidate[legNum] = legMode;
				}
				legNum++;
			}
			// chain-based modes must finish at the location of the last activity of the plan
			Iterator<BasicLeg.Mode> modeTrackerCheck = modeTracker.keySet().iterator();
			while(modeChainIsFeasible && modeTrackerCheck.hasNext()) {
				BasicLeg.Mode mode = modeTrackerCheck.next();
				Location currentLocation = modeTracker.get(mode);
				if (!currentLocation.equals(plan.getFirstActivity().getFacility()) && !currentLocation.equals(plan.getLastActivity().getFacility())) {
//					System.out.println("Mode " + mode + " is not at the location of either the first or the last activity.");
					modeChainIsFeasible = false;
				}
			}
			if (modeChainIsFeasible) {
				this.result.add(candidate);
			}
//			System.out.println();
//			System.out.flush();

		}
	}

}
