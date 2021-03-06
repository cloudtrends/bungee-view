/* 

 Created on Jul 7, 2006

 The Bungee View applet lets you search, browse, and data-mine an image collection.  
 Copyright (C) 2006  Mark Derthick

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.  See gpl.html.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 You may also contact the author at 
 mad@cs.cmu.edu, 
 or at
 Mark Derthick
 Carnegie-Mellon University
 Human-Computer Interaction Institute
 Pittsburgh, PA 15213

 */

package edu.cmu.cs.bungee.piccoloUtils.gui;

import java.awt.Color;

import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PInputEvent;

public class Boundary extends LazyPNode {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final double w = 2;

	LazyPNode parent;

	static BoundaryHandler handler = new BoundaryHandler();

	public String mouseDoc = "Start dragging boundary";

	public double minX;

	public double maxX;

	protected double x0 = -9999; // origin for dragging

	private boolean isHorizontal;

	private double constrainedX0;

	/**
	 * Add this to the x- or y-coordinate
	 */
	public double margin = 0;

	// public Boundary(double x, double y, double h, double _minX, double _maxX,
	// Color baseColor) {
	// init(x, y, h, _minX, _maxX, baseColor);
	// }

	public Boundary(LazyPNode _parent, boolean _isHorizontal) {
		parent = _parent;
		isHorizontal = _isHorizontal;
		if (isHorizontal)
			setHeight(w);
		else
			setWidth(w);
		setVisible(false);
		setBaseColor(null);
		addInputEventListener(handler);
		parent.addChild(this);
		validate();
	}

	// void init(double x, double y, double w1, double h, Color baseColor) {
	// // System.out.println(centerX + " " + y + " " + h);
	// setVisible(false);
	// setWidth(w1);
	// setHeight(h);
	// setBaseColor(baseColor);
	// setOffset(x, y);
	// addInputEventListener(handler);
	// }

	public void validate() {
		if (isHorizontal) {
			setWidth(parent.getWidth());
			setOffset(getXOffset(), parent.getHeight() + offset());
		} else {
			setHeight(parent.getHeight());
			setOffset(parent.getWidth() + offset(), getYOffset());
		}
	}

	public void setBaseColor(Color baseColor) {
		if (baseColor == null)
			baseColor = Color.white;
		setPaint(baseColor);
	}

	private double offset() {
		return margin - w / 2;
	}

	// public void setMinX (double _minX) {
	// minX = (int) (_minX + 0.5);
	// }
	//	
	// public void setMaxX (double _maxX) {
	// maxX = (int) (_maxX + 0.5);
	// }

	public double center() {
		return constrainedX0;
	}

	public void setCenter(double center) {
		if (isHorizontal)
			setOffset(getXOffset(), center + offset());
		else
			setOffset(center + offset(), getYOffset());
	}

	public void exit() {
		if (x0 < -9990) {
			setVisible(false);
			parent.exitBoundary(this);
		}
		setMouseDoc(null);
	}

	public void enter() {
		// Util.print("boundary enter ");
		setVisible(true);
		setMouseDoc(mouseDoc);
		parent.enterBoundary(this);
	}

	public void setMouseDoc(String doc) {
		// override this
		if (parent instanceof MouseDoc)
			((MouseDoc) parent).setMouseDoc(doc);
	}

	// public void mayHideTransients(PNode node) {
	// assert edu.cmu.cs.bungee.javaExtensions.Util.ignore(node);
	// System.err.println("Should override Boundary.mayHideTransients: " +
	// parent);
	// }

	public void drag(PInputEvent e) {
		double dx = isHorizontal ? e.getDelta().getHeight() : e.getDelta()
				.getWidth();
		x0 += dx;
		constrainedX0 = Math.min(maxX, Math.max(minX, x0));
		// System.out.println("b " + x0);
		// setOffset(getXOffset(), x - w / 2);
		parent.updateBoundary(this);
	}

	public void startDrag() {
		if (isHorizontal) {
			x0 = getYOffset() - offset();
			minX = parent.minHeight(this);
			maxX = parent.maxHeight(this);
		} else {
			x0 = getXOffset() - offset();
			minX = parent.minWidth(this);
			maxX = parent.maxWidth(this);
		}
		assert minX == Math.round(minX) : getParent();
		assert maxX == Math.round(maxX) : getParent();
	}

	public void endDrag() {
		x0 = -9999;
		setVisible(false);
		parent.exitBoundary(this);
	}
}

final class BoundaryHandler extends MyInputEventHandler {

	public BoundaryHandler() {
		super(Boundary.class);
	}

	public boolean exit(PNode node) {
		((Boundary) node).exit();
		return true;
	}

	public boolean enter(PNode node) {
		((Boundary) node).enter();
		return true;
	}

	// protected void mayHideTransients(PNode node) {
	// ((Boundary) node).mayHideTransients(node);
	// }

	public boolean press(PNode node) {
		((Boundary) node).startDrag();
		return true;
	}

	public boolean drag(PNode node, PInputEvent e) {
		((Boundary) node).drag(e);
		return true;
	}

	public boolean release(PNode node) {
		((Boundary) node).endDrag();
		return true;
	}

}
