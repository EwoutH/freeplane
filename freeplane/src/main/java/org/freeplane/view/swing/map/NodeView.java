/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is modified by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.view.swing.map;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Window;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.freeplane.api.ChildNodesAlignment;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.IUserInputListenerFactory;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.ObjectRule;
import org.freeplane.features.DashVariant;
import org.freeplane.features.attribute.AttributeController;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.cloud.CloudController;
import org.freeplane.features.cloud.CloudModel;
import org.freeplane.features.edge.EdgeController;
import org.freeplane.features.edge.EdgeController.Rules;
import org.freeplane.features.edge.EdgeStyle;
import org.freeplane.features.filter.Filter;
import org.freeplane.features.filter.FilterController;
import org.freeplane.features.filter.hidden.NodeVisibility;
import org.freeplane.features.filter.hidden.NodeVisibilityConfiguration;
import org.freeplane.features.highlight.HighlightController;
import org.freeplane.features.highlight.NodeHighlighter;
import org.freeplane.features.icon.hierarchicalicons.HierarchicalIcons;
import org.freeplane.features.map.EncryptionModel;
import org.freeplane.features.map.FreeNode;
import org.freeplane.features.map.HistoryInformationModel;
import org.freeplane.features.map.INodeView;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeDeletionEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.NodeModel.NodeChangeType;
import org.freeplane.features.map.NodeModel.Side;
import org.freeplane.features.map.SummaryNode;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.nodelocation.LocationController;
import org.freeplane.features.nodelocation.LocationModel;
import org.freeplane.features.nodestyle.NodeStyleController;
import org.freeplane.features.nodestyle.NodeStyleShape;
import org.freeplane.features.styles.LogicalStyleController.StyleOption;
import org.freeplane.features.styles.MapViewLayout;
import org.freeplane.features.text.TextController;
import org.freeplane.view.swing.map.attribute.AttributeView;
import org.freeplane.view.swing.map.cloud.CloudView;
import org.freeplane.view.swing.map.cloud.CloudViewFactory;
import org.freeplane.view.swing.map.edge.AutomaticEdgeStyle;
import org.freeplane.view.swing.map.edge.EdgeView;
import org.freeplane.view.swing.map.edge.EdgeViewFactory;

/**
 * This class represents a single Node of a MindMap (in analogy to
 * TreeCellRenderer).
 */
public class NodeView extends JComponent implements INodeView {
	static final String DEBUG_INFO_PROPERTY = "debugInfo";
	private static final int HIGHLIGHTED_NODE_ARC_MARGIN = 4;
	final static int ALIGN_BOTTOM = -1;
	final static int ALIGN_CENTER = 0;
	final static int ALIGN_TOP = 1;
	protected final static Color dragColor = Color.lightGray;
	public final static int DRAGGED_OVER_NO = 0;
	public final static int DRAGGED_OVER_SIBLING = 2;
	public final static int DRAGGED_OVER_SON = 1;
	/** For RootNodeView. */
	public final static int DRAGGED_OVER_SON_LEFT = 3;
	private static final long serialVersionUID = 1L;
	static final int SPACE_AROUND = 50;
	public static final int MAIN_VIEWER_POSITION = 1;
	public static final int NOTE_VIEWER_POSITION = 10;
	final static boolean PAINT_DEBUG_INFO;
	static {
		boolean paintDebugInfo = false;
		try{
			paintDebugInfo = Boolean.getBoolean("org.freeplane.view.swing.map.NodeView.PAINT_DEBUG_INFO");
		}
		catch(Exception e){
		}
		PAINT_DEBUG_INFO = paintDebugInfo;
	}
	static private int maxToolTipWidth;
	private AttributeView attributeView;
	private JComponent contentPane;
	private MainView mainView;
	private final MapView map;
	private NodeModel model;
	private NodeView preferredChild;
	private EdgeStyle edgeStyle = EdgeStyle.EDGESTYLE_HIDDEN;
	private Integer edgeWidth = 1;
	private ObjectRule<Color, Rules> edgeColor = null;
	private Color modelBackgroundColor;

	private int topOverlap;
	private int bottomOverlap;
	private boolean isFolded;
	private DashVariant edgeDash = DashVariant.DEFAULT;

	public static final int DETAIL_VIEWER_POSITION = 2;

	protected NodeView(final NodeModel model, final MapView map, final Container parent) {
		setFocusCycleRoot(true);
		this.model = model;
		this.map = map;
		this.isFolded = map.getModeController().getMapController().isFolded(model);
	}

	public boolean isFolded(){
		return isFolded;
	}

	void addDragListener(final DragGestureListener dgl) {
		if (dgl == null) {
			return;
		}
		final DragSource dragSource = DragSource.getDefaultDragSource();
		dragSource.createDefaultDragGestureRecognizer(getMainView(), DnDConstants.ACTION_COPY
		        | DnDConstants.ACTION_MOVE | DnDConstants.ACTION_LINK, dgl);
	}

	void addDropListener(final DropTargetListener dtl) {
		if (dtl == null) {
			return;
		}
		final DropTarget dropTarget = new DropTarget(getMainView(), dtl);
		dropTarget.setActive(true);
	}

	private int calcShiftY(final LocationModel locationModel) {
		try {
			final NodeModel parent = model.getParentNode();
			Filter filter = map.getFilter();
			int singleChildShift = ! getParentView().isSummary() && getModeController().hasOneVisibleChild(parent, filter)
			        ? getMainView().getSingleChildShift() : 0;
            return locationModel.getShiftY().toBaseUnitsRounded() + singleChildShift;
		}
		catch (final NullPointerException e) {
			return 0;
		}
	}

	public static int ADDITIONAL_MOUSE_SENSITIVE_AREA = 50;

	@Override
	public boolean contains(final int x, final int y) {
		final int space = map.getZoomed(NodeView.SPACE_AROUND);
		final int reducedSpace = space - ADDITIONAL_MOUSE_SENSITIVE_AREA;
		if (x >= reducedSpace && x < getWidth() - reducedSpace && y >= reducedSpace && y < getHeight() - reducedSpace){
			for(int i = getComponentCount()-1; i >= 0; i--){
				final Component comp = getComponent(i);
				if(comp.isVisible() && comp.contains(x-comp.getX(), y-comp.getY()))
					return true;
			}
		}
		return false;
	}

	protected void convertPointToMap(final Point p) {
		UITools.convertPointToAncestor(this, p, map);
	}

	public void createAttributeView() {
		if (attributeView == null && NodeAttributeTableModel.getModel(model) != NodeAttributeTableModel.EMTPY_ATTRIBUTES) {
			attributeView = new AttributeView(this, true);
		}
		syncronizeAttributeView();
	}

	public boolean focused() {
		return mainView.hasFocus();
	}

	public AttributeView getAttributeView() {
		if (attributeView == null) {
			AttributeController.getController(getModeController()).createAttributeTableModel(model);
			attributeView = new AttributeView(this, true);
		}
		return attributeView;
	}

	public Color getBackgroundColor() {
		final Color cloudColor = getCloudColor();
		if (cloudColor != null) {
			return cloudColor;
		}
		final NodeView parentView = getParentView();
		if (parentView == null) {
			return map.getBackground();
		}
		return parentView.getBackgroundColor();
	}

	public Color getCloudColor() {
	    final CloudModel cloudModel = getCloudModel();
		if(cloudModel != null){
			final Color cloudColor = cloudModel.getColor();
			return cloudColor;
		}
		return null;
    }

	/**
	 * This method returns the NodeViews that are children of this node.
	 */
	public LinkedList<NodeView> getChildrenViews() {
		final LinkedList<NodeView> childrenViews = new LinkedList<NodeView>();
		final Component[] components = getComponents();
		for (int i = 0; i < components.length; i++) {
			if (!(components[i] instanceof NodeView)) {
				continue;
			}
			final NodeView view = (NodeView) components[i];
			childrenViews.add(view);
		}
		return childrenViews;
	}

	public JComponent getContent() {
		final JComponent c = contentPane == null ? mainView : contentPane;
		assert (c == null || c.getParent() == this);
		return c;
	}

	private Container getContentPane() {
		if (contentPane == null) {
			Window windowAncestor = SwingUtilities.getWindowAncestor(mainView);
			boolean hasFocus = windowAncestor != null && windowAncestor.getMostRecentFocusOwner() == mainView;
			contentPane = NodeViewFactory.getInstance().newContentPane(this);
			final int index = getComponentCount() - 1;
			remove(index);
			contentPane.add(mainView);
			mainView.putClientProperty("NODE_VIEW_CONTENT_POSITION", MAIN_VIEWER_POSITION);
			if(! mainView.isVisible())
				mainView.setVisible(true);
			add(contentPane, index);
			if(hasFocus)
				restoreFocusToMainView();
		}
		return contentPane;
	}

	private void restoreFocusToMainView() {
		final Window windowAncestor = SwingUtilities.getWindowAncestor(mainView);
		if(windowAncestor.isFocused())
			mainView.requestFocusInWindow();
		else
			windowAncestor.addWindowFocusListener(new WindowFocusListener() {
				@Override
				public void windowLostFocus(WindowEvent e) {
				}

				@Override
				public void windowGainedFocus(WindowEvent e) {
					mainView.requestFocusInWindow();
					windowAncestor.removeWindowFocusListener(this);
				}
			});
	}

	/**
	 * Returns the coordinates occupied by the node and its children as a vector
	 * of four point per node.
	 */
	public void getCoordinates(final LinkedList<Point> inList) {
		getCoordinates(inList, 0, false, 0, 0);
	}

	private void getCoordinates(final LinkedList<Point> inList, int additionalDistanceForConvexHull,
	                            final boolean byChildren, final int transX, final int transY) {
		if (!isVisible()) {
			return;
		}
		if (isContentVisible()) {
			if (byChildren) {
				final ModeController modeController = getModeController();
				final CloudController cloudController = CloudController.getController(modeController);
				final CloudModel cloud = cloudController.getCloud(getModel(), getStyleOption());
				if (cloud != null) {
					additionalDistanceForConvexHull += CloudView.getAdditionalHeigth(cloud, this) / 5;
				}
			}
			final int x = transX + getContent().getX() - getDeltaX();
			final int y = transY + getContent().getY() - getDeltaY();
			final int width = mainView.getMainViewWidthWithFoldingMark();
			final int heightWithFoldingMark = mainView.getMainViewHeightWithFoldingMark(true);
			final int height = Math.max(heightWithFoldingMark, getContent().getHeight());
			inList.addLast(new Point(-additionalDistanceForConvexHull + x, -additionalDistanceForConvexHull + y));
			inList
			    .addLast(new Point(-additionalDistanceForConvexHull + x, additionalDistanceForConvexHull + y + height));
			inList.addLast(new Point(additionalDistanceForConvexHull + x + width, additionalDistanceForConvexHull + y
			        + height));
			inList
			    .addLast(new Point(additionalDistanceForConvexHull + x + width, -additionalDistanceForConvexHull + y));
		}
		for (final NodeView child : getChildrenViews()) {
			child.getCoordinates(inList, additionalDistanceForConvexHull, true, transX + child.getX(),
			    transY + child.getY());
		}
	}

	/** get x coordinate including folding symbol */
	public int getDeltaX() {
		return mainView.getDeltaX();
	}

	/** get y coordinate including folding symbol */
	public int getDeltaY() {
		return mainView.getDeltaY();
	}

	private NodeView getFirst(Component startAfter, final boolean leftOnly, final boolean rightOnly) {
		final Component[] components = getComponents();
		for (int i = 0; i < components.length; i++) {
			if (startAfter != null) {
				if (components[i] == startAfter) {
					startAfter = null;
				}
				continue;
			}
			if (!(components[i] instanceof NodeView)) {
				continue;
			}
			final NodeView view = (NodeView) components[i];
			if (leftOnly && !view.isLeft() || rightOnly && view.isLeft()) {
				continue;
			}
			if (view.isContentVisible()) {
				return view;
			}
			if(! view.isSummary()) {
				final NodeView child = view.getFirst(null, leftOnly, rightOnly);
				if (child != null) {
					return child;
				}
			}
		}
		return null;
	}

	public int getHGap() {
		return map.getZoomed(LocationModel.getModel(model).getHGap().toBaseUnits());
	}

	private NodeView getLast(Component startBefore, final boolean leftOnly, final boolean rightOnly) {
		final Component[] components = getComponents();
		for (int i = components.length - 1; i >= 0; i--) {
			if (startBefore != null) {
				if (components[i] == startBefore) {
					startBefore = null;
				}
				continue;
			}
			if (!(components[i] instanceof NodeView)) {
				continue;
			}
			final NodeView view = (NodeView) components[i];
			if (leftOnly && !view.isLeft() || rightOnly && view.isLeft()) {
				continue;
			}
			if (view.isContentVisible()) {
				return view;
			}
			if(! view.isSummary()) {
				final NodeView child = view.getLast(null, leftOnly, rightOnly);
				if (child != null) {
					return child;
				}
			}
		}
		return null;
	}

	LinkedList<NodeView> getLeft(final boolean onlyVisible) {
		final LinkedList<NodeView> left = new LinkedList<NodeView>();
		for (final NodeView node : getChildrenViews()) {
			if (node == null) {
				continue;
			}
			if (node.isLeft()) {
				left.add(node);
			}
		}
		return left;
	}

	/**
	 * Returns the Point where the Links should arrive the Node.
	 */
	public Point getLinkPoint(final Point declination) {
		int x, y;
		Point linkPoint;
		if (declination != null) {
			x = map.getZoomed(declination.x);
			y = map.getZoomed(declination.y);
		}
		else {
			x = 1;
			y = 0;
		}
		if (isLeft()) {
			x = -x;
		}
		if (y != 0) {
			final double ctgRect = Math.abs((double) getContent().getWidth() / getContent().getHeight());
			final double ctgLine = Math.abs((double) x / y);
			int absLinkX, absLinkY;
			if (ctgRect >= ctgLine) {
				absLinkX = Math.abs(x * getContent().getHeight() / (2 * y));
				absLinkY = getContent().getHeight() / 2;
			}
			else {
				absLinkX = getContent().getWidth() / 2;
				absLinkY = Math.abs(y * getContent().getWidth() / (2 * x));
			}
			linkPoint = new Point(getContent().getWidth() / 2 + (x > 0 ? absLinkX : -absLinkX), getContent()
			    .getHeight() / 2 + (y > 0 ? absLinkY : -absLinkY));
		}
		else {
			linkPoint = new Point((x > 0 ? getContent().getWidth() : 0), (getContent().getHeight() / 2));
		}
		linkPoint.translate(getContent().getX(), getContent().getY());
		convertPointToMap(linkPoint);
		return linkPoint;
	}

	public MainView getMainView() {
		return mainView;
	}

    public Point getRelativeLocation(NodeView target) {
        Component component;
        int targetX = 0;
        int targetY = 0;
        for(component = target.getMainView();
            !(this.equals(component) || component.getClass().equals(MapView.class));
            component = component.getParent()){
            targetX += component.getX();
            targetY += component.getY();
        }
        Point relativeLocation = new Point();
        UITools.convertPointToAncestor(mainView, relativeLocation, component);
        relativeLocation.x = targetX - relativeLocation.x;
        relativeLocation.y = targetY - relativeLocation.y;
        return relativeLocation;
    }

	public MapView getMap() {
		return map;
	}

	public int getMaxToolTipWidth() {
		if (maxToolTipWidth == 0) {
			try {
				maxToolTipWidth = ResourceController.getResourceController().getIntProperty(
				    "toolTipManager.max_tooltip_width", 600);
			}
			catch (final NumberFormatException e) {
				maxToolTipWidth = 600;
			}
		}
		return maxToolTipWidth;
	}

	public NodeModel getModel() {
		return model;
	}

	private NodeView getNextSiblingSingle() {
		LinkedList<NodeView> v = getSiblingViews();
		final int index = v.indexOf(this);
		boolean skipUntilSummaryEnd = isSummary();
		for (int i = index + 1; i < v.size(); i++) {
			final NodeView nextView = v.get(i);
			if(skipUntilSummaryEnd && nextView.isSummary())
				break;
			skipUntilSummaryEnd = false;
			final NodeModel node = nextView.getModel();
			if (node.hasVisibleContent(map.getFilter())) {
				return nextView;
			}
			else if (! node.isHiddenSummary()){
				final NodeView first = nextView.getFirst(null, false, false);
				if (first != null) {
					return first;
				}
			}
		}
		return this;
	}

	NodeView getNextVisibleSibling() {
		NodeView sibling;
		NodeView lastSibling = this;
		for (sibling = this; sibling != map.getRoot(); sibling = sibling.getParentView()) {
			lastSibling = sibling;
			sibling = sibling.getNextSiblingSingle();
			if (sibling != lastSibling) {
				break;
			}
		}
		while (sibling.getModel().getNodeLevel(map.getFilter()) < map.getSiblingMaxLevel()) {
			final NodeView first = sibling.getFirst(sibling.isRoot() ? lastSibling : null, this.isLeft(),
			    !this.isLeft());
			if (first == null) {
				break;
			}
			sibling = first;
		}
		if (sibling.isRoot()) {
			return this;
		}
		return sibling;
	}

	public NodeView getParentView() {
		final Container parent = getParent();
		if (parent instanceof NodeView) {
			return (NodeView) parent;
		}
		return null;
	}

	public NodeView getPreferredVisibleChild(final boolean getUpper, final boolean left) {
		if (getModel().isLeaf()) {
			return null;
		}
		if (getUpper) {
			preferredChild = null;
		}
		if (preferredChild != null && (left == preferredChild.isLeft()) && preferredChild.getParent() == this) {
			if (preferredChild.isContentVisible()) {
				return preferredChild;
			}
			else {
				final NodeView newSelected = preferredChild.getPreferredVisibleChild(getUpper, left);
				if (newSelected != null) {
					return newSelected;
				}
			}
		}
		int yGap = Integer.MAX_VALUE;
		final NodeView baseComponent;
		if (isContentVisible()) {
			baseComponent = this;
		}
		else {
			baseComponent = getAncestorWithVisibleContent();
		}
		final int ownX = baseComponent.getContent().getX() + baseComponent.getContent().getWidth() / 2;
		final int ownY = baseComponent.getContent().getY() + baseComponent.getContent().getHeight() / 2;
		NodeView newSelected = null;
		for (int i = 0; i < getComponentCount(); i++) {
			final Component c = getComponent(i);
			if (!(c instanceof NodeView)) {
				continue;
			}
			NodeView childView = (NodeView) c;
			if (!(childView.isLeft() == left)) {
				continue;
			}
			if (!childView.isContentVisible()) {
				childView = childView.getPreferredVisibleChild(getUpper, left);
				if (childView == null) {
					continue;
				}
			}
			if (getUpper) {
				return childView;
			}
			final JComponent childContent = childView.getContent();
			if(childContent == null)
				continue;
			final Point childPoint = new Point(left ? childContent.getWidth() : 0, childContent.getHeight() / 2);
			UITools.convertPointToAncestor(childContent, childPoint, baseComponent);
			final int dy = childPoint.y - ownY;
			final int dx = childPoint.x - ownX;
			final int gapToChild = dy*dy + dx*dx;
			if (gapToChild < yGap) {
				newSelected = childView;
				preferredChild = (NodeView) c;
				yGap = gapToChild;
			}
			else {
				break;
			}
		}
		return newSelected;
	}

	private NodeView getPreviousSiblingSingle() {
		LinkedList<NodeView> v = getSiblingViews();
		final int index = v.indexOf(this);
		boolean skipUntilFirstGroupNode = isSummary();
		for (int i = index - 1; i >= 0; i--) {
			final NodeView nextView = v.get(i);
 			if(skipUntilFirstGroupNode) {
 				skipUntilFirstGroupNode = !nextView.isFirstGroupNode();
 				continue;
 			}
			final NodeModel node = nextView.getModel();
			if (node.hasVisibleContent(map.getFilter())) {
				return nextView;
			}
			else if (! node.isHiddenSummary()){
				final NodeView last = nextView.getLast(null, false, false);
				if (last != null) {
					return last;
				}
			}
		}
		return this;
	}

	protected LinkedList<NodeView> getSiblingViews() {
		LinkedList<NodeView> v = null;
		final NodeView parentView = getParentView();
		if (parentView == null){
			UITools.errorMessage("unexpected error: node " + getMainView().getText() + " has lost its parent ");
		}
		if (parentView.isRoot()) {
			if (this.isLeft()) {
				v = parentView.getLeft(true);
			}
			else {
				v = parentView.getRight(true);
			}
		}
		else {
			v = parentView.getChildrenViews();
		}
		return v;
	}

	NodeView getPreviousVisibleSibling() {
		NodeView sibling;
		NodeView previousSibling = this;
		for (sibling = this; sibling != map.getRoot(); sibling = sibling.getParentView()) {
			previousSibling = sibling;
			sibling = sibling.getPreviousSiblingSingle();
			if (sibling != previousSibling) {
				break;
			}
		}
		while (sibling.getModel().getNodeLevel(map.getFilter()) < map.getSiblingMaxLevel()) {
			final NodeView last = sibling.getLast(sibling.isRoot() ? previousSibling : null, this.isLeft(),
			    !this.isLeft());
			if (last == null) {
				break;
			}
			sibling = last;
		}
		if (sibling.isRoot()) {
			return this;
		}
		return sibling;
	}

	LinkedList<NodeView> getRight(final boolean onlyVisible) {
		final LinkedList<NodeView> right = new LinkedList<NodeView>();
		for (final NodeView node : getChildrenViews()) {
			if (node == null) {
				continue;
			}
			if (!node.isLeft()) {
				right.add(node);
			}
		}
		return right;
	}

	/**
		 * @return Returns the sHIFT.s
		 */
	public int getShift() {
		final LocationModel locationModel = LocationModel.getModel(model);
		return map.getZoomed(calcShiftY(locationModel));
	}

    public Color getTextBackground() {
        if (modelBackgroundColor != null) {
            return modelBackgroundColor;
        }
        return getBackgroundColor();
    }

    public void setTextBackground(Color textBackground) {
        modelBackgroundColor = textBackground;
    }

    public Color getTextBackground(StyleOption styleOption) {
        Color modelBackgroundColor = styleController().getBackgroundColor(model, styleOption);
        if (modelBackgroundColor != null) {
            return modelBackgroundColor;
        }
        return getBackgroundColor();
    }

    private NodeStyleController styleController() {
        return NodeStyleController.getController(getModeController());
    }

	public Color getTextColor(StyleOption styleOption) {
		final Color color = styleController().getColor(model, styleOption);
		return color;
	}

	/**
	 * @return Returns the VGAP.
	 */
	public int getMinimalDistanceBetweenChildren() {
		final double minimalDistanceBetweenChildren = getModeController().getExtension(LocationController.class).getMinimalDistanceBetweenChildren(model).toBaseUnits();
		return map.getZoomed(minimalDistanceBetweenChildren);
	}
	
	ChildNodesAlignment getChildNodesAlignment() {
		ChildNodesAlignment verticalAlignment = getModeController().getExtension(LocationController.class).getChildNodesAlignment(model);
		switch (verticalAlignment) {
		case UNDEFINED:
		case AS_PARENT:
			return getParentViewChildNodesAlignment();
		default:
			return verticalAlignment;
		}
	}

	private ChildNodesAlignment getParentViewChildNodesAlignment() {
		NodeView parentView = getParentView();
		if (parentView == null)
			return ChildNodesAlignment.BY_CENTER;
		else if(parentView.isSummary())
			return parentView.getParentViewChildNodesAlignment();
		else
			return parentView.getChildNodesAlignment();
	}

	public NodeView getAncestorWithVisibleContent() {
		final Container parent = getParent();
		if (!(parent instanceof NodeView)) {
			return null;
		}
		final NodeView parentView = (NodeView) parent;
		if (parentView.isContentVisible()) {
			return parentView;
		}
		return parentView.getAncestorWithVisibleContent();
	}

	public NodeView getVisibleSummarizedOrParentView() {
		final Container parent = getParent();
		if (!(parent instanceof NodeView)) {
			return null;
		}
		final NodeView parentView = (NodeView) parent;
		if(isSummary()){
			boolean startFromSummary = true;
			LinkedList<NodeView> v = getSiblingViews();
			final int index = v.indexOf(this);
			for (int i = index - 1; i >= 0; i--) {
				final NodeView nextView = v.get(i);
				if (nextView.isContentVisible()) {
					return nextView;
				}
				if(! nextView.isSummary())
					startFromSummary = false;
				else if(! startFromSummary)
					break;

			}
		}
		if (parentView.isContentVisible()) {
			return parentView;
		}
		return parentView.getVisibleSummarizedOrParentView();
	}

	public int getZoomedFoldingSymbolHalfWidth() {
		final int preferredFoldingSymbolHalfWidth = (int) ((ResourceController.getResourceController().getIntProperty("foldingsymbolwidth", 10) * map.getZoom()) / 2);
		return preferredFoldingSymbolHalfWidth;
	}

	public int getZoomedStateSymbolHalfWidth() {
		final int preferredFoldingSymbolHalfWidth = (int) ((ResourceController.getResourceController().getIntProperty("statesymbolwidth", 10) * map.getZoom()) / 2);
		return preferredFoldingSymbolHalfWidth;
	}

	void addChildViews() {
		if(isFolded)
			return;
		int index = 0;
		for (NodeModel child : getModel().getChildren()) {
			if(isChildHidden(child))
				return;
			if(getComponentCount() <= index
					|| ! (getComponent(index) instanceof NodeView))
				addChildView(child, index++);
		}
	}

	void addChildView(final NodeModel newNode, int index) {
			NodeViewFactory.getInstance().newNodeView(newNode, map, this, index);
	}

	/* fc, 25.1.2004: Refactoring necessary: should call the model. */
	public boolean isChildOf(final NodeView myNodeView) {
		return getParentView() == myNodeView;
	}

	/**
	 */
	public boolean isContentVisible() {
		if(isValid())
			return getContent().isVisible();
		else
			return getModel().hasVisibleContent(map.getFilter());
	}

	public boolean isLeft() {
		if (map.getLayoutType() == MapViewLayout.OUTLINE) {
			return false;
		}
		return getModel().isLeft(map.getRoot().getModel());
	}


	public boolean isRight() {
		return ! isLeft() && getModel() != map.getRoot().getModel();
	}

	public boolean isParentHidden() {
		final Container parent = getParent();
		if (!(parent instanceof NodeView)) {
			return false;
		}
		final NodeView parentView = (NodeView) parent;
		return !parentView.isContentVisible() && ! parentView.getModel().isHiddenSummary();
	}

	/* fc, 25.1.2004: Refactoring necessary: should call the model. */
	public boolean isParentOf(final NodeView myNodeView) {
		return (this == myNodeView.getParentView());
	}

	public boolean isRoot() {
		return map.isRoot(this);
	}

	public boolean isSelected() {
		return (map.isSelected(this));
	}

	/* fc, 25.1.2004: Refactoring necessary: should call the model. */
	public boolean isSiblingOf(final NodeView myNodeView) {
		return getParentView() == myNodeView.getParentView();
	}

	@Override
	public void nodeChanged(final NodeChangeEvent event) {
		final NodeModel node = event.getNode();
		// is node is deleted, skip the rest.
		if (!node.isRoot() && node.getParentNode() == null) {
			return;
		}
		final Object property = event.getProperty();
		if (property == NodeChangeType.FOLDING || property == Properties.HIDDEN_CHILDREN || property == EncryptionModel.class) {
			if(map.isSelected() || property == EncryptionModel.class && ! isFolded){
				boolean folded = getModeController().getMapController().isFolded(model);
				boolean force = property ==Properties.HIDDEN_CHILDREN || property == EncryptionModel.class;
				setFolded(folded, force);
			}
			if(property != EncryptionModel.class)
				return;
		}
		if(property == ChildNodesAlignment.class) {
			invalidateAll();
			revalidate();
			repaint();
			return;
		}
		if(property == NodeVisibilityConfiguration.class) {
			updateAll();
			if(event.getNewValue() != NodeVisibilityConfiguration.SHOW_HIDDEN_NODES)
			    FilterController.getCurrentFilterController().selectVisibleNodes(map.getMapSelection());
			return;
		}

		if(property == NodeVisibility.class
				&& node.getMap().getRootNode().getExtension(NodeVisibilityConfiguration.class) != NodeVisibilityConfiguration.SHOW_HIDDEN_NODES) {
			final NodeView parentView = getParentView();
			parentView.setFolded(parentView.isFolded, true);
            if(event.getNewValue() == NodeVisibility.HIDDEN && isSelected())
                FilterController.getCurrentFilterController().selectVisibleNodes(map.getMapSelection());
			return;
		}
		
		if(property == Side.class) {
			invalidateAll();
			revalidate();
		}

		// is node is not fully initialized, skip the rest.
		if (mainView == null) {
			return;
		}
		if (property.equals(NodeModel.NODE_ICON) || property.equals(HierarchicalIcons.ICONS)) {
			mainView.updateIcons(this);
			revalidate();
			return;
		}
		if (property.equals(NodeModel.NODE_ICON_SIZE))
		{
			mainView.updateIcons(this);
			revalidate();
			return;
		}

		if (property.equals(HistoryInformationModel.class)) {
			return;
		}
		update();
		NodeView parentView = getParentView();
		NodeModel parentNode = node.getParentNode();
		if (parentNode != null && parentView != null) {
			parentView.numberingChanged(parentNode.getIndex(node) + 1);
		}
	}

	public void setFolded(boolean folded) {
		setFolded(folded, false);
		revalidate();
	}

	private void setFolded(boolean folded, boolean force) {
		boolean wasFolded = isFolded;
		this.isFolded = folded;
		if(wasFolded != isFolded || force) {
			treeStructureChanged();
			map.selectIfSelectionIsEmpty(this);
			NodeStyleShape shape = styleController().getShape(model, getStyleOption());
			if (shape.equals(NodeStyleShape.combined))
				update();
		}
	}

	@Override
	public void onNodeDeleted(NodeDeletionEvent nodeDeletionEvent) {
		NodeModel mapRootNode = map.getRoot().getModel();
		if(mapRootNode == nodeDeletionEvent.node)
			map.restoreRootNode(nodeDeletionEvent.index);
		else if (mapRootNode.isDescendantOf(nodeDeletionEvent.node))
			map.restoreRootNode();
		if (nodeDeletionEvent.index >= getComponentCount() - 1) {
			return;
		}
		final boolean preferredChildIsLeft = preferredChild != null 
		        && map.getLayoutType() != MapViewLayout.OUTLINE
		        && preferredChild.getModel().wouldBeLeft(mapRootNode, getModel());
		final NodeView node = (NodeView) getComponent(nodeDeletionEvent.index);
		if (node == preferredChild) {
			preferredChild = null;
			for (int j = nodeDeletionEvent.index + 1; j < getComponentCount(); j++) {
				final Component c = getComponent(j);
				if (!(c instanceof NodeView)) {
					break;
				}
				final NodeView candidate = (NodeView) c;
				if (candidate.isVisible() && node.isLeft() == candidate.isLeft()) {
					preferredChild = candidate;
					break;
				}
			}
			if (preferredChild == null) {
				for (int j = nodeDeletionEvent.index - 1; j >= 0; j--) {
					final Component c = getComponent(j);
					if (!(c instanceof NodeView)) {
						break;
					}
					final NodeView candidate = (NodeView) c;
					if (candidate.isVisible() && node.isLeft() == candidate.isLeft()) {
						preferredChild = candidate;
						break;
					}
				}
			}
		}
		numberingChanged(nodeDeletionEvent.index+1);
		map.preserveRootNodeLocationOnScreen();
		node.remove();
		NodeView preferred = getPreferredVisibleChild(false, preferredChildIsLeft);
		if (preferred == null) {
			preferred = this;
		}
		revalidate();
		if(map.getSelected() ==  null)
			map.selectVisibleAncestorOrSelf(preferred);
	}

	@Override
	public void onNodeInserted(final NodeModel parent, final NodeModel child, final int index) {
		assert parent == model;
		if (isFolded) {
			return;
		}
		addChildView(child, index);
		numberingChanged(index + 1);
		revalidate();
	}

	// updates children, starting from firstChangedIndex, if necessary.
	private void numberingChanged(int firstChangedIndex) {
		final TextController textController = TextController.getController(getModeController());
		if (firstChangedIndex > 0 || textController.getNodeNumbering(getModel())) {
			final Component[] components = getComponents();
			for (int i = firstChangedIndex; i < components.length; i++) {
				if (components[i] instanceof NodeView) {
					final NodeView view = (NodeView) components[i];
					final MainView childMainView = view.getMainView();
					if(childMainView != null){
						childMainView.updateText(view.getModel());
						view.numberingChanged(0);
					}
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see javax.swing.JComponent#paint(java.awt.Graphics)
	 */
	@Override
	public void paintComponent(final Graphics g) {
		if(getMainView() == null)
			return;
		final PaintingMode paintingMode = map.getPaintingMode();
		if(paintingMode == null){
			LogUtils.severe("paintingMode = null");
			LogUtils.severe("own map ="  + map);
			final MapView ancestorMap = (MapView) SwingUtilities.getAncestorOfClass(MapView.class, this);
			LogUtils.severe("parent component map ="  + ancestorMap);
			if(ancestorMap != null)
				LogUtils.severe("ancestor map paintingMode = " + ancestorMap.getPaintingMode());
			throw new NullPointerException();
		}
		final Graphics2D g2 = (Graphics2D) g;
		final ModeController modeController = getModeController();
		final Object renderingHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		switch (paintingMode) {
		case CLOUDS:
		    if (isSubtreeVisible()) {
		        modeController.getController().getMapViewManager().setEdgesRenderingHint(g2);
		        final boolean isRoot = isRoot();
		        if (isRoot) {
		            paintCloud(g);
		        }
		        paintClouds(g2);
		        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, renderingHint);
		    }
		    break;
		case NODES:
		    if (isContentVisible()) {
		        g2.setStroke(MainView.DEF_STROKE);
		        modeController.getController().getMapViewManager().setEdgesRenderingHint(g2);
		        paintEdges(g2, this);
		        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, renderingHint);
		    }
		    break;
		default:
		    break;
		}
		if (PAINT_DEBUG_INFO && isSelected() && paintingMode.equals(PaintingMode.SELECTED_NODES)){
			final int spaceAround = getZoomed(SPACE_AROUND);
			g.setColor(UITools.getTextColorForBackground(getBackgroundColor()));
			g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			g.drawRect(spaceAround - 1, spaceAround - 1, getWidth() - 2 * spaceAround, getHeight() - 2 * spaceAround);
			Object debugInfo = getClientProperty(DEBUG_INFO_PROPERTY);
			if(debugInfo != null)
				g.drawString(debugInfo.toString(), 0, spaceAround);
		}
	}

	@Override
    public void paint(Graphics g) {
		if(isSubtreeVisible()) {
			super.paint(g);
			paintDecoration((Graphics2D) g);
		}
    }

	private void paintCloud(final Graphics g) {
		if (!isSubtreeVisible()) {
			return;
		}
		final CloudModel cloudModel = getCloudModel();
		if (cloudModel == null) {
			return;
		}
		final CloudView cloud = new CloudViewFactory().createCloudView(cloudModel, this);
		cloud.paint(g);
	}

    private void paintClouds(final Graphics2D g) {
        for (int i = getComponentCount() - 1; i >= 0; i--) {
            final Component component = getComponent(i);
            if (!(component instanceof NodeView)) {
                continue;
            }
            final NodeView nodeView = (NodeView) component;
            final Point p = new Point();
            UITools.convertPointToAncestor(nodeView, p, this);
            g.translate(p.x, p.y);
            if (nodeView.isSubtreeVisible()) {
                nodeView.paintCloud(g);
            }
            else {
                nodeView.paintClouds(g);
            }
            g.translate(-p.x, -p.y);
        }
    }

    private void paintEdges(final Graphics2D g, NodeView source) {
    	boolean isRoot  = isRoot();
		SummaryEdgePainter summaryEdgePainter = new SummaryEdgePainter(this, isRoot ? true : isLeft());
    	SummaryEdgePainter rightSummaryEdgePainter =  isRoot ? new SummaryEdgePainter(this, false) : null;
        final int start;
        final int end;
        final int step;
        if (map.getLayoutType() == MapViewLayout.OUTLINE){
        	start = getComponentCount() - 1;
        	end = -1;
        	step = -1;
        }
        else{
        	start = 0;
        	end = getComponentCount();
        	step = 1;
        }
		for (int i = start; i != end; i+=step) {
            final Component component = getComponent(i);
            if (!(component instanceof NodeView)) {
                continue;
            }
            final NodeView nodeView = (NodeView) component;
        	if (map.getLayoutType() != MapViewLayout.OUTLINE) {
        		SummaryEdgePainter activePainter = nodeView.isLeft() || !isRoot ? summaryEdgePainter : rightSummaryEdgePainter;
        		activePainter.addChild(nodeView);
        		if(activePainter.paintSummaryEdge(g, source, nodeView)){
        			if(! nodeView.isContentVisible()){
        				final Rectangle bounds =  SwingUtilities.convertRectangle(this, nodeView.getBounds(), source);
        				final Graphics cg = g.create(bounds.x, bounds.y, bounds.width, bounds.height);
        				try{
        					nodeView.paintEdges((Graphics2D) cg, nodeView);
        				}
        				finally{
        					cg.dispose();
        				}

        			}
        			continue;
        		}
            }
        	if (nodeView.isContentVisible()) {
        		final EdgeView edge = EdgeViewFactory.getInstance().getEdge(source, nodeView, source);
        		edge.paint(g);
        	}
        	else {
        		nodeView.paintEdges(g, source);
        	}
        }
    }


	int getSpaceAround() {
		return getZoomed(NodeView.SPACE_AROUND);
	}

	public int getZoomed(int x) {
		return map.getZoomed(x);
	}

	private void paintDecoration(final Graphics2D g) {
		final PaintingMode paintingMode = map.getPaintingMode();
		if(! (getMainView() != null &&
				( paintingMode.equals(PaintingMode.NODES) && !isSelected() || paintingMode.equals(PaintingMode.SELECTED_NODES) && isSelected())
				&& isContentVisible()))
			return;
		final Graphics2D g2 = g;
		final ModeController modeController = getModeController();
		final Object renderingHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g2.setStroke(MainView.DEF_STROKE);
		modeController.getController().getMapViewManager().setEdgesRenderingHint(g2);
		final Point origin = new Point();
		UITools.convertPointToAncestor(mainView, origin, this);
		g.translate(origin.x, origin.y);
		mainView.paintDecoration(this, g);
		g.translate(-origin.x, -origin.y);
		if (map.isSelected()) {
			final HighlightController highlightController = getModeController().getController().getExtension(HighlightController.class);
			final List<NodeHighlighter> highlighters = highlightController.getHighlighters(model, map.isPrinting());
			int margin = HIGHLIGHTED_NODE_ARC_MARGIN;
			for(NodeHighlighter highlighter : highlighters){
				margin += HIGHLIGHTED_NODE_ARC_MARGIN;
				highlightNode(g, highlighter, margin);
			}
		}
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, renderingHint);
	}

	public void highlightNode(final Graphics2D g, NodeHighlighter highlighter, final int arcMargin) {
		final Color oldColor = g.getColor();
		final Stroke oldStroke = g.getStroke();
		g.setStroke(NodeHighlighter.DEFAULT_STROKE);
		highlighter.configure(g, map.isPrinting());
		final JComponent content = getContent();
		Point contentLocation = content.getLocation();
		final int arcWidth = 15;
		g.drawRoundRect(contentLocation.x - arcMargin, contentLocation.y - arcMargin, content.getWidth() + 2 * arcMargin,
		    content.getHeight() + 2 * arcMargin, arcWidth, arcWidth);
		g.setColor(oldColor);
		g.setStroke(oldStroke);
	}

	/**
	 * This is a bit problematic, because getChildrenViews() only works if model
	 * is not yet removed. (So do not _really_ delete the model before the view
	 * removed (it needs to stay in memory)
	 */
	void remove() {
		for (final ListIterator<NodeView> e = getChildrenViews().listIterator(); e.hasNext();) {
			NodeView child = e.next();
			child.remove();
		}
		getModeController().onViewRemoved(this);
		removeFromMap();
		if (attributeView != null) {
			attributeView.viewRemoved();
		}
		getModel().removeViewer(this);
		map.deselect(this);
	}

	protected void removeFromMap() {
		setFocusCycleRoot(false);
		Container parent = getParent();
		parent.remove(this);
	}

	private void repaintEdge(final NodeView target) {
		if (target.map.getLayoutType() == MapViewLayout.OUTLINE){
			target.getAncestorWithVisibleContent().repaint();
			return;
		}
		final Point relativeLocation = getRelativeLocation(target);
        final MainView targetMainView = target.getMainView();
        relativeLocation.x += targetMainView.getWidth()/2;
        relativeLocation.y += targetMainView.getHeight()/2;
        final Point inPoint = mainView.getConnectorPoint(relativeLocation);
        UITools.convertPointToAncestor(targetMainView, inPoint, this);

        relativeLocation.x -= targetMainView.getWidth()/2;
        relativeLocation.y -= targetMainView.getHeight()/2;
        relativeLocation.x = - relativeLocation.x + mainView.getWidth()/2;
        relativeLocation.y = - relativeLocation.y + mainView.getHeight()/2;
		final Point outPoint = targetMainView.getConnectorPoint(relativeLocation);
		UITools.convertPointToAncestor(getMainView(), outPoint, this);

		final int x = Math.min(inPoint.x, outPoint.x);
		final int y = Math.min(inPoint.y, outPoint.y);
		final int w = Math.abs(inPoint.x - outPoint.x);
		final int h = Math.abs(inPoint.y - outPoint.y);
		final int EXTRA = 50;
		repaint(x - EXTRA, y - EXTRA, w + EXTRA * 2, h + EXTRA * 2);
	}

	void repaintSelected() {
		// return if main view was not set
		if (mainView == null) {
			return;
		}
		// do not repaint removed nodes
		if (model.getParentNode() == null && !model.isRoot()) {
			return;
		}
		if (getEdgeStyle().equals(EdgeStyle.EDGESTYLE_HIDDEN)) {
			final NodeView visibleParentView = getAncestorWithVisibleContent();
			if (visibleParentView != null) {
				visibleParentView.repaintEdge(this);
			}
		}
		final JComponent content = getContent();
		final int EXTRA = 20;
		final int x = content.getX() - EXTRA;
		final int y = content.getY() - EXTRA;
		repaint(x, y, content.getWidth() + EXTRA * 2, content.getHeight() + EXTRA * 2);
	}

	@Override
	public boolean requestFocusInWindow() {
		if (mainView == null) {
			return false;
		}
		if (mainView.requestFocusInWindow()) {
			map.scrollNodeToVisible(this);
			Controller.getCurrentController().getViewController().addObjectTypeInfo(getModel().getUserObject());
			return true;
		}
		return false;
	}

	@Override
	public void requestFocus() {
		if (mainView == null) {
			return;
		}
		map.scrollNodeToVisible(this);
		Controller.getCurrentController().getViewController().addObjectTypeInfo(getModel().getUserObject());
		mainView.requestFocus();
	}

	void setMainView(final MainView newMainView) {
		if (contentPane != null) {
			assert (contentPane.getParent() == this);
			if (mainView != null)
				removeContent(MAIN_VIEWER_POSITION);
			addContent(newMainView, MAIN_VIEWER_POSITION);
			assert (contentPane.getParent() == this);
		}
		else if (mainView != null) {
			final Container c = mainView.getParent();
			int i;
			for (i = c.getComponentCount() - 1; i >= 0 && mainView != c.getComponent(i); i--) {
				;
			}
			c.remove(i);
			c.add(newMainView, i);
		}
		else {
			add(newMainView);
		}
		mainView = newMainView;
		ModeController modeController = getModeController();
		if(modeController.canEdit(getModel())) {
			final IUserInputListenerFactory userInputListenerFactory = modeController
					.getUserInputListenerFactory();
			mainView.addMouseListener(userInputListenerFactory.getNodeMouseMotionListener());
			mainView.addMouseMotionListener(userInputListenerFactory.getNodeMouseMotionListener());
			mainView.addMouseWheelListener(userInputListenerFactory.getNodeMouseWheelListener());
			mainView.addKeyListener(userInputListenerFactory.getNodeKeyListener());
			addDragListener(userInputListenerFactory.getNodeDragListener());
			addDropListener(userInputListenerFactory.getNodeDropTargetListener());
		}
	}

	protected void setModel(final NodeModel model) {
		this.model = model;
	}

	public void setPreferredChild(final NodeView view) {
		if(view != null && ! SummaryNode.isSummaryNode(view.getModel()))
			preferredChild = view;
		final Container parent = this.getParent();
		if (view == null) {
			return;
		}
		else if (parent instanceof NodeView) {
			((NodeView) parent).setPreferredChild(this);
		}
	}

	/**
	 */
	public void setText(final String string) {
		mainView.setText(string);
	}


	void syncronizeAttributeView() {
		if (attributeView != null) {
			attributeView.syncronizeAttributeView();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see java.awt.Component#toString()
	 */
	@Override
	public String toString() {
		return getModel().toString() + ", " + super.toString();
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * javax.swing.event.TreeModelListener#treeStructureChanged(javax.swing.
	 * event.TreeModelEvent)
	 */
	private void treeStructureChanged() {
		map.preserveRootNodeLocationOnScreen();
		for (NodeView child : getChildrenViews()) {
			child.remove();
		}
		addChildViews();
		map.revalidateSelecteds();
		revalidate();
	}

	public void update() {
		if(! map.isDisplayable())
			return;
		invalidate();
		updateShape();
		updateEdge();
		updateCloud();
		if (!isContentVisible()) {
			mainView.setVisible(false);
			return;
		}
		mainView.setVisible(true);
		mainView.updateTextColor(this);
		mainView.updateCss(this);
		mainView.updateFont(this);
		mainView.updateHorizontalTextAlignment(this);
		mainView.updateBorder(this);
		final ModeController modeController = getModeController();
        final NodeStyleController nsc = NodeStyleController.getController(modeController);
        StyleOption styleOption = getStyleOption();
        final int minNodeWidth = map.getZoomed(nsc.getMinWidth(getModel(), styleOption).toBaseUnits());
        final int maxNodeWidth = Math.max(map.getLayoutSpecificMaxNodeWidth(), map.getZoomed(nsc.getMaxWidth(getModel(), styleOption).toBaseUnits()));
        mainView.setMinimumWidth(minNodeWidth);
        mainView.setMaximumWidth(maxNodeWidth);

		createAttributeView();
		if (attributeView != null) {
			attributeView.update();
		}
		final boolean textShortened = isShortened();

		if(! textShortened){
			final NodeViewFactory nodeViewFactory = NodeViewFactory.getInstance();
			nodeViewFactory.updateDetails(this, minNodeWidth, maxNodeWidth);
			nodeViewFactory.updateNoteViewer(this, minNodeWidth, maxNodeWidth);
			if (contentPane != null) {
				final int componentCount = contentPane.getComponentCount();
				for (int i = 1; i < componentCount; i++) {
					final Component component = contentPane.getComponent(i);
					if (component instanceof JComponent) {
						((JComponent) component).revalidate();
					}
				}
			}
		}
		updateShortener(getModel(), textShortened);
		mainView.updateIcons(this);
		mainView.updateText(getModel());
		modelBackgroundColor = styleController().getBackgroundColor(model, getStyleOption());
		revalidate();
		repaint();
	}

    private ModeController getModeController() {
        return map.getModeController();
    }

	public boolean isShortened() {
	    final ModeController modeController = getModeController();
		final TextController textController = TextController.getController(modeController);
		final boolean textShortened = textController.isMinimized(getModel());
	    return textShortened;
    }

	private void updateEdge() {
        final EdgeController edgeController = EdgeController.getController(getModeController());
		this.edgeStyle = edgeController.getStyle(model, getStyleOption(), false);
		final NodeModel realNode = SummaryNode.getRealNode(model);
		this.edgeWidth = edgeController.getWidth(realNode, getStyleOption(), false);
		this.edgeDash = edgeController.getDash(realNode, getStyleOption(), false);
		final ObjectRule<Color, Rules> newColor = edgeController.getColorRule(realNode, getStyleOption());
		this.edgeColor = newColor;
		final NodeModel parentNode = model.getParentNode();
		if(parentNode != null && SummaryNode.isSummaryNode(parentNode))
			getParentView().updateEdge();
    }

	public EdgeStyle getEdgeStyle() {
		if(edgeStyle != null)
			return edgeStyle;
		final NodeView parentView = getParentView();
		if(parentView != null)
			return parentView.getEdgeStyle();
		return EdgeStyle.values()[0];
    }

	public DashVariant getEdgeDash() {
		if(edgeDash != null)
		    return edgeDash;
		final NodeView parentView = getParentView();
		if(parentView != null)
			return parentView.getEdgeDash();
		return DashVariant.DEFAULT;
    }

	public int getEdgeWidth() {
		if(edgeWidth != null)
		    return edgeWidth;
		final NodeView parentView = getParentView();
		if(parentView != null)
			return parentView.getEdgeWidth();
		return 1;
    }

	public Color getEdgeColor() {
		if(edgeColor.hasValue())
			return edgeColor.getValue();
		Rules rule = edgeColor.getRule();
		if(rule == EdgeController.Rules.BY_COLUMN){
			final Color color = new AutomaticEdgeStyle(this).getColor();
			edgeColor.setCache(color);
			return color;
		}
		final NodeModel parentNode = model.getParentNode();
		if(rule == EdgeController.Rules.BY_BRANCH && parentNode.isRoot()
				|| rule == EdgeController.Rules.BY_LEVEL){
			final int index;
			if (rule == EdgeController.Rules.BY_BRANCH)
				index = parentNode.getIndex(model) + 1;
			else
				index = model.getNodeLevel(map.getFilter()) + (model.isHiddenSummary() ? 1 : 0);
			final MapModel mapModel = map.getModel();
			ModeController modeController = getModeController();
			EdgeController edgeController = modeController.getExtension(EdgeController.class);
			if(edgeController.areEdgeColorsAvailable(mapModel)){
				Color color = edgeController.getEdgeColor(mapModel, index);
				edgeColor.setCache(color);
				return color;
			}
		}
		else
			if(rule == EdgeController.Rules.BY_PARENT) {
			final NodeView parentView = getParentView();
			if (parentView != null) {
				final Color color = parentView.getEdgeColor();
				return color;
			}
		}
		return Color.GRAY;
    }

	private void updateCloud() {
		final CloudModel cloudModel = CloudController.getController(getModeController()).getCloud(model, getStyleOption());
		putClientProperty(CloudModel.class, cloudModel);
    }

	public CloudModel getCloudModel() {
		return (CloudModel) getClientProperty(CloudModel.class);
    }

	private void updateShortener(NodeModel nodeModel, boolean textShortened) {
		final boolean componentsVisible = !textShortened;
		setContentComponentVisible(componentsVisible);
	}

	private void setContentComponentVisible(final boolean componentsVisible) {
		if(contentPane == null)
			return;
		final Component[] components = getContentPane().getComponents();
		int index;
		for (index = 0; index < components.length; index++) {
			final Component component = components[index];
			if (component == getMainView()) {
				continue;
			}
			if (component.isVisible() != componentsVisible) {
				component.setVisible(componentsVisible);
			}
		}
	}

	public void updateAll() {
		update();
		invalidate();
		for (final NodeView child : getChildrenViews()) {
			child.updateAll();
		}
	}
	private void invalidateAll() {
		LinkedList<NodeView> childrenViews = getChildrenViews();
		if(childrenViews.isEmpty())
			invalidate();
		for (final NodeView child : childrenViews) {
			child.invalidateAll();
		}
	}
	
	

	private void updateShape() {
		if(mainView != null) {
			NodeViewFactory.getInstance().updateViewPainter(this);
		}
		else {
			final MainView newMainView = NodeViewFactory.getInstance().newMainView(this);
			setMainView(newMainView);
			if (map.getSelected() == this) {
				requestFocusInWindow();
			}
		}
	}

	boolean useSelectionColors() {
		return isSelected() && !MapView.drawsRectangleForSelection() && !map.isPrinting();
	}

	@Override
	protected void validateTree() {
		super.validateTree();
	}

	public void addContent(JComponent component, int pos) {
		component.putClientProperty("NODE_VIEW_CONTENT_POSITION", pos);
		final Container contentPane = getContentPane();
		for (int i = 0; i < contentPane.getComponentCount(); i++) {
			JComponent content = (JComponent) contentPane.getComponent(i);
			if (content == null)
				throw new RuntimeException("component " + i + "is null");
			final Object clientProperty = content.getClientProperty("NODE_VIEW_CONTENT_POSITION");
			if (clientProperty == null)
				throw new RuntimeException("NODE_VIEW_CONTENT_POSITION not set on component " + content.toString() + i
				        + "/" + contentPane.getComponentCount());
			if (pos < (Integer) clientProperty) {
				contentPane.add(component, i);
				return;
			}
		}
		contentPane.add(component);
	}

	public JComponent removeContent(int pos) {
		return removeContent(pos, true);
	}

	private JComponent removeContent(int pos, boolean remove) {
		if (contentPane == null)
			return null;
		for (int i = 0; i < contentPane.getComponentCount(); i++) {
			JComponent component = (JComponent) contentPane.getComponent(i);
			Integer contentPos = (Integer) component.getClientProperty("NODE_VIEW_CONTENT_POSITION");
			if (contentPos == null) {
				continue;
			}
			if (contentPos == pos) {
				if (remove) {
					component.putClientProperty("NODE_VIEW_CONTENT_POSITION", null);
					contentPane.remove(i);
				}
				return component;
			}
			if (contentPos > pos) {
				return null;
			}
		}
		return null;
	}

	public JComponent getContent(int pos) {
		return removeContent(pos, false);
	}

	public boolean isSummary() {
		return SummaryNode.isSummaryNode(getModel());
	}

	public boolean isFirstGroupNode() {
		return SummaryNode.isFirstGroupNode(getModel());
	}

	public boolean isFree() {
		return FreeNode.isFreeNode(getModel());
	}

 	int getTopOverlap() {
		return topOverlap;
	}

	void setTopOverlap(int topOverlap) {
		this.topOverlap = topOverlap;
	}

	int getBottomOverlap() {
		return bottomOverlap;
	}

	void setBottomOverlap(int bottomOverlap) {
		this.bottomOverlap = bottomOverlap;
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		Rules rule = edgeColor.getRule();
		if(EdgeController.Rules.BY_PARENT != rule)
			edgeColor.resetCache();
		super.setBounds(x, y, width, height);
	}

	boolean isSubtreeVisible() {
	    if(isValid())
	        return getHeight() > 2 * getSpaceAround();
	    if(isContentVisible())
	        return true;
	    final Component[] components = getComponents();
	    for (int i = 0; i < components.length; i++) {
	        if (!(components[i] instanceof NodeView)) {
	            continue;
	        }
	        final NodeView view = (NodeView) components[i];
	        if(view.isSubtreeVisible())
	            return true;
	    }
	    return false;
	}



	public enum Properties{HIDDEN_CHILDREN};

	boolean isChildHidden(NodeModel node) {
		@SuppressWarnings("unchecked")
		final Set<NodeModel> hiddenChildren = (Set<NodeModel>) getClientProperty(Properties.HIDDEN_CHILDREN);
		return hiddenChildren != null && hiddenChildren.contains(node);
	}

	public int getHiddenChildCount() {
		@SuppressWarnings("unchecked")
		final Set<NodeModel> hiddenChildren = (Set<NodeModel>) getClientProperty(Properties.HIDDEN_CHILDREN);
		return hiddenChildren != null ? hiddenChildren.size() : 0;
	}

	boolean hasHiddenChildren() {
		@SuppressWarnings("unchecked")
		final Set<NodeModel> hiddenChildren = (Set<NodeModel>) getClientProperty(Properties.HIDDEN_CHILDREN);
		return hiddenChildren != null && ! hiddenChildren.isEmpty();
	}

	boolean unfoldHiddenChildren() {
		final boolean hasHiddenChildren = hasHiddenChildren();
		putClientProperty(Properties.HIDDEN_CHILDREN, null);
		return hasHiddenChildren;
	}

	public void hideChildren(NodeModel node) {
		final HashSet<NodeModel> set = new HashSet<>();
		set.addAll(node.getChildren());
		putClientProperty(Properties.HIDDEN_CHILDREN, set);
	}

	public boolean showHiddenNode(NodeModel node) {
		@SuppressWarnings("unchecked")
		final Set<NodeModel> hiddenChildren = (Set<NodeModel>) getClientProperty(Properties.HIDDEN_CHILDREN);
		return hiddenChildren != null && hiddenChildren.remove(node);
	}

    public Rectangle getInnerBounds() {
        int spaceAround = getSpaceAround();
        if (isContentVisible())
            return new Rectangle(spaceAround, spaceAround, getWidth() - 2 * spaceAround, getHeight() - 2 * spaceAround);
        else {
            Rectangle innerBounds = new Rectangle(spaceAround, spaceAround, -1, -1);
            getChildrenViews().stream()
            .map(v -> {
                Rectangle r = v.getInnerBounds();
                r.x += v.getX();
                r.y += v.getY();
                return r;
            })
            .forEach(innerBounds::add);
            innerBounds.y = spaceAround;
            innerBounds.width = Math.max(0, innerBounds.width);
            innerBounds.height = getHeight() - 2 * spaceAround;
            return innerBounds;
        }
    }

    public StyleOption getStyleOption() {
        return useSelectionColors() ? StyleOption.FOR_SELECTED_NODE : StyleOption.FOR_UNSELECTED_NODE;
    }
}
