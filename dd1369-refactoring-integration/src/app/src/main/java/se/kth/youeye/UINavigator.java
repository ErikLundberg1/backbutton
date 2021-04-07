package se.kth.youeye;

import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;

import static android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK;
import static android.content.Context.WINDOW_SERVICE;

/**
 * The UINavigator class serves the purpose of navigating the screen by taking an InputEvent and
 * performing an action related to that event.
 */

public class UINavigator {

    // We use the @IntDef notation to ensure safer handling of our our magic constants
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NO_ACTION, SELECT_NEXT, CLICK, BACK})
    public @interface ActionTypeDef {}
    // Magic constant definitions
    public static final int NO_ACTION = 0; // event to act on
    public static final int SELECT_NEXT = 1; // event to act on
    public static final int CLICK = 2; // event to act on
    public static final int BACK = 3; // universal back command

    private final MainService mainService;
    private final FrameLayout layout;
    private int currentNodeIndex;
    private List<AccessibilityNodeInfo> clickableNodes;
    private ImageView selectedHighlighter;
    private static final String TAG = "UINavigator";
    TextView debugView;

    public UINavigator(MainService mainService) {
        this.mainService = mainService;

        Log.d("objectname", "onServiceConnected: " + this);
        // Set up the window manager
        WindowManager wm = (WindowManager) mainService.getSystemService(WINDOW_SERVICE);
        layout = new FrameLayout(mainService);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        // Currently disabled since we can't press the debug buttons if the flag is set:
        // lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; // To pass through touch events to the underlying window.
        lp.width = WindowManager.LayoutParams.MATCH_PARENT; // Fill the screen.
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.TOP;
        LayoutInflater inflater = LayoutInflater.from(mainService);
        inflater.inflate(R.layout.action_bar, layout);
        wm.addView(layout, lp);

        // Debug view
        debugView = layout.findViewById(R.id.textView);


        currentNodeIndex = 0;
        clickableNodes = getClickableChildren(mainService.getRootInActiveWindow());
        Log.d("click", "onServiceConnected: clickableNodes" + clickableNodes);

        // Set up the highlighting rectangle
        ShapeDrawable shapeDrawable = new ShapeDrawable(new RectShape());
        shapeDrawable.setAlpha(100);
        shapeDrawable.setIntrinsicWidth(1); // These can be any positive integer, it seems.
        shapeDrawable.setIntrinsicHeight(1);
        selectedHighlighter = new ImageView(mainService);
        selectedHighlighter.setImageDrawable(shapeDrawable);
        selectedHighlighter.setScaleType(ImageView.ScaleType.FIT_XY);
        layout.addView(selectedHighlighter);
        highlightNode(clickableNodes.get(currentNodeIndex));

        // Set up the navigation buttons
        configureNextButton();
        configureClickButton();
        configureBackButton();
    }

    /**
     * On events, this method calls a suitable method to perform an action depending on the type of
     * the event.
     * @param eventType the event type: SELECT_NEXT or CLICK
     */
    public void handleEvent(int eventType) {
        Log.d("click", "Handling event: " + eventType);

        switch (eventType) {
            case SELECT_NEXT:
                selectNext();
                break;
            case CLICK:
                click();
            case BACK:
                mainService.performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    /**
     * Click on the currently selected node.
     */
    private void click() {
        clickableNodes.get(currentNodeIndex).performAction(AccessibilityNodeInfo.ACTION_CLICK);

        // Debug
        CharSequence s = clickableNodes.get(currentNodeIndex).getClassName();
        if (s == null) s = "text = null";
        debugView.setText(s);
    }

    /**
     * Traverse to the next node of the UI tree
     * TODO: Maybe use getTraversalAfter() instead?
     */
    private void selectNext() {
        // Update child index
        if (clickableNodes != null && clickableNodes.size() > 0) {
            boolean hasVisibleNodes = false;
            // TODO: This should maybe be done in updateNodeInfos()
            // Checks that we have at least one visible node.
            for (int i = 0; i < clickableNodes.size(); i++) {
                if (clickableNodes.get(i).isVisibleToUser()) {
                    hasVisibleNodes = true;
                    break;
                }
            }
            if (!hasVisibleNodes) {
                Log.d(TAG, "selectNext: current tree has no nodes visible to the user!");
                return;
            }
            currentNodeIndex++;
            if (currentNodeIndex >= clickableNodes.size())
                currentNodeIndex = 0;
            if (!clickableNodes.get(currentNodeIndex).isVisibleToUser())
                selectNext();
            highlightNode(clickableNodes.get(currentNodeIndex)); // Continue if selected is invisible.
        } else {
            Log.d(TAG, "selectNext: no clickable nodes");
        }
    }

    /**
     * Updates the accessibility node info. Will be called when node info changes
     * @param root node to be updated
     */
    public void resetNodeInfos(AccessibilityNodeInfo root) {
        clickableNodes = getClickableChildren(root);
        currentNodeIndex = clickableNodes.size() - 1;
        selectNext();
        currentNodeIndex = Math.max(currentNodeIndex, 0);
    }

    public void refreshNodeInfos(AccessibilityNodeInfo root) {
        Log.d(TAG, "refreshNodeInfos: We entered");
        AccessibilityNodeInfo selected = clickableNodes.get(currentNodeIndex);
        resetNodeInfos(root);
        for (int i = 0; i < clickableNodes.size(); i++) {
            if (clickableNodes.get(i).equals(selected)) {
                currentNodeIndex = i;
                Log.d(TAG, "refreshNodeInfos: We found a node, index " + currentNodeIndex);
                highlightNode(clickableNodes.get(currentNodeIndex));
                return;
            }
        }
        Log.d(TAG, "refreshNodeInfos: No node was found.");
    }

    /**
     * Returns the relevant children of the currently selected node. Relevant: nodes used by user
     * @param parent parent node
     */
    private List<AccessibilityNodeInfo> getClickableChildren(AccessibilityNodeInfo parent) {
        if (parent == null) {
            throw new NullPointerException("Tried to get children of null parent.");
        }

        List<AccessibilityNodeInfo> clickableNodes = new ArrayList<>();
        Queue<AccessibilityNodeInfo> nodesWithChildren = new ArrayDeque<AccessibilityNodeInfo>();
        nodesWithChildren.add(parent);

        while (!nodesWithChildren.isEmpty()) {
            AccessibilityNodeInfo parentNode = nodesWithChildren.remove();
            for (int i = 0; i < parentNode.getChildCount(); i++) {
                AccessibilityNodeInfo childNode = parentNode.getChild(i);
                if (childNode.isClickable())
                    clickableNodes.add(childNode);
                // TODO: Should we still add the children of a clickable node?
                if (childNode.getChildCount() > 0)
                    nodesWithChildren.add(childNode);
            }
        }

        Collections.sort(clickableNodes, new Comparator<AccessibilityNodeInfo>() {
            @Override
            public int compare(AccessibilityNodeInfo o1, AccessibilityNodeInfo o2) {
                Rect o1Rect = new Rect();
                Rect o2Rect = new Rect();
                o1.getBoundsInScreen(o1Rect);
                o2.getBoundsInScreen(o2Rect);
                if (o1Rect.top != o2Rect.top)
                    return o1Rect.top - o2Rect.top;
                return o1Rect.left - o2Rect.left;
            }
        });

        return clickableNodes;
    }

    /**
     * Draws a box around the selected node. Changes the layoutParams and coordinates of the
     * selectedHighlighter to match the bounds for the AccessibilityNodeInfo.
     * @param nodeInfo info about the node to be highlighted
     */
    private void highlightNode (AccessibilityNodeInfo nodeInfo) {
        Rect boundsRect = new Rect();
        clickableNodes.get(currentNodeIndex).getBoundsInScreen(boundsRect);
        selectedHighlighter.requestLayout();
        selectedHighlighter.getLayoutParams().width = boundsRect.right - boundsRect.left;
        selectedHighlighter.getLayoutParams().height = boundsRect.bottom - boundsRect.top;
        selectedHighlighter.setX(boundsRect.left);
        selectedHighlighter.setY(boundsRect.top);
    }

    /**
     * Debug shit
     * Cycles the children nodes below the current node iteratively
     * by choosing the next node and so on.
     */
    private void configureNextButton() {
        Button findButton = layout.findViewById(R.id.find);
        findButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectNext();
            }
        });
    }

    /**
     * Debug shit
     * If the currently selected node, children.get(selectedChildIndex), is
     * clickable, we click it and set the currentNode to the root node.
     * If not, we set that node to current node.
     * Either way, we reset selectedChildIndex and children.
     */
    private void configureClickButton() {
        Button clickButton = layout.findViewById(R.id.click);
        clickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                click();
            }
        });
    }

    /**
     * Implements a debug button that when pressed will give a back button press that works in all apps
     */
    private void configureBackButton() {
        Button clickButton = layout.findViewById(R.id.back);
        clickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean result = mainService.performGlobalAction(GLOBAL_ACTION_BACK); // returns true if the command is successful
            }
        });
    }

}
