package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.tabs.impl.JBTabsImpl;

import javax.swing.*;
import java.awt.*;

public abstract class LayoutStrategy {

  SingleRowLayout myLayout;
  JBTabsImpl myTabs;

  protected LayoutStrategy(final SingleRowLayout layout) {
    myLayout = layout;
    myTabs = myLayout.myTabs;
  }

  abstract int getMoreRectAxisSize();

  abstract void setComponentAddins(final SingleRowPassInfo data, JComponent toolbar, final boolean toolbarVertical);

  public abstract int getStartPosition(final SingleRowPassInfo data);

  public abstract int getToFitLength(final SingleRowPassInfo data);

  public abstract int getLengthIncrement(final Dimension dimension);

  public abstract int getMaxPosition(final Rectangle bounds);

  public abstract int getFixedFitLength(final SingleRowPassInfo data);

  public abstract Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength);

  public abstract int getFixedPosition(final SingleRowPassInfo data);

  public abstract Rectangle getMoreRect(final SingleRowPassInfo data);

  public abstract int getComponentPosition(final SingleRowPassInfo data);

  public abstract Rectangle getToolbarRec(final SingleRowPassInfo data, final JComponent selectedToolbar);

  public abstract Point getCompPoint(final SingleRowPassInfo data);

  static class Top extends LayoutStrategy {

    Top(final SingleRowLayout layout) {
      super(layout);
    }

    void setComponentAddins(final SingleRowPassInfo data, final JComponent toolbar, final boolean toolbarVertical) {
      if (myTabs.isSideComponentVertical()) {
        data.componentFixedPosition = toolbar.getPreferredSize().width + 1;
      }
    }

    public int getStartPosition(final SingleRowPassInfo data) {
      return data.insets.left;
    }

    public int getMoreRectAxisSize() {
      return myLayout.myMoreIcon.getIconWidth() + 6;
    }

    public int getToFitLength(final SingleRowPassInfo data) {
      return myTabs.getWidth() - data.insets.left - data.insets.right - (data.displayedHToolbar ? myTabs.getToolbarInset() : 0);
    }

    public int getLengthIncrement(final Dimension labelPrefSize) {
      return labelPrefSize.width;
    }

    public int getMaxPosition(final Rectangle bounds) {
      return (int)bounds.getMaxX();
    }

    public int getFixedFitLength(final SingleRowPassInfo data) {
      return myTabs.myHeaderFitSize.height;
    }

    public Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength) {
      return new Rectangle(position, fixedPos, length, fixedFitLength);
    }

    public int getFixedPosition(final SingleRowPassInfo data) {
      return data.insets.top;
    }

    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      return new Rectangle(myTabs.getWidth() - data.insets.right - data.moreRectAxisSize, data.insets.top + myTabs.getSelectionTabVShift(),
                                            data.moreRectAxisSize - 1, myTabs.myHeaderFitSize.height - 1);
    }

    public int getComponentPosition(final SingleRowPassInfo data) {
      return myTabs.isHideTabs() ? data.insets.top : myTabs.myHeaderFitSize.height + data.insets.top + (myTabs.isStealthModeEffective() ? 0 : 1);
    }

    public Rectangle getToolbarRec(final SingleRowPassInfo data, final JComponent selectedToolbar) {
      if (!myTabs.isSideComponentVertical() && !myTabs.isHideTabs()) {
        int toolbarX = data.position + myTabs.getToolbarInset() + (data.moreRect != null ? data.moreRect.width : 0);
        final Rectangle rec =
          new Rectangle(toolbarX, data.insets.top, myTabs.getSize().width - data.insets.left - toolbarX, myTabs.myHeaderFitSize.height - 1);
        return rec;
      }
      else if (myTabs.isSideComponentVertical()) {
        final Rectangle rec = new Rectangle(data.insets.left + 1, data.compPosition + 1, selectedToolbar.getPreferredSize().width,
                                            myTabs.getSize().height - data.compPosition - data.insets.bottom - 2);
        return rec;
      }

      return null;
    }

    public Point getCompPoint(final SingleRowPassInfo data) {
      return new Point(data.componentFixedPosition, data.compPosition);
    }
  }

  static class Left extends LayoutStrategy {
    Left(final SingleRowLayout layout) {
      super(layout);
    }

    int getMoreRectAxisSize() {
      return myLayout.myMoreIcon.getIconHeight() + 6;
    }

    void setComponentAddins(final SingleRowPassInfo data, final JComponent toolbar, final boolean toolbarVertical) {

    }

    public int getStartPosition(final SingleRowPassInfo data) {
      return data.insets.top;
    }

    public int getToFitLength(final SingleRowPassInfo data) {
      return myTabs.getHeight() - data.insets.top - data.insets.bottom;
    }

    public int getLengthIncrement(final Dimension labelPrefSize) {
      return labelPrefSize.height;
    }

    public int getMaxPosition(final Rectangle bounds) {
      return (int)bounds.getMaxY();
    }

    public int getFixedFitLength(final SingleRowPassInfo data) {
      return myTabs.myHeaderFitSize.width;
    }

    public Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength) {
      return new Rectangle(fixedPos, position, fixedFitLength, length);
    }

    public int getFixedPosition(final SingleRowPassInfo data) {
      return data.insets.left;
    }

    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      return new Rectangle(myTabs.getHeight() - data.insets.bottom - data.moreRectAxisSize, data.insets.left + myTabs.getSelectionTabVShift(), myTabs.myHeaderFitSize.width - 1, data.moreRectAxisSize - 1);
    }

    public int getComponentPosition(final SingleRowPassInfo data) {
      return myTabs.isHideTabs() ? data.insets.left : myTabs.myHeaderFitSize.width + data.insets.left;
    }

    public Rectangle getToolbarRec(final SingleRowPassInfo data, final JComponent selectedToolbar) {
      return null;
    }

    public Point getCompPoint(final SingleRowPassInfo data) {
      return new Point(data.compPosition, data.componentFixedPosition);
    }
  }

}