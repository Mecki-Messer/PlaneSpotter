package planespotter.display;

import planespotter.constants.Bounds;

import javax.swing.*;
import java.awt.*;

import static planespotter.constants.GUIConstants.*;

/**
 * @name PanelModels
 * @author jml04
 * @version 1.0
 *
 * contains panel models for GUI
 */
public final class PanelModels {

    //default desktop width
    static int WIDTH_RIGHT = 1259-280;
    static int WIDTH_LEFT = 1259-WIDTH_RIGHT; // unnötig (=279)
    // large menu item width
    static int WIDTH_MENUITEM = WIDTH_LEFT-25;

    /**
     * main-panel
     */
    public static JPanel mainPanel (JFrame parent) {
        // TODO: setting up mainpanel
        JPanel mainpanel = new JPanel();
        mainpanel.setBounds(0, 0, parent.getWidth(), parent.getHeight()); // mainpanel width: 1260
        mainpanel.setLayout(null);
        mainpanel.setBackground(DEFAULT_BG_COLOR);
        return mainpanel;
    }

    /**
     * title panel with bground and title
     */
    public static JPanel titlePanel (JPanel parent) {
        // TODO: setting up title panel
        JPanel title = new JPanel();
        title.setBounds(0, 0, parent.getWidth(), 70);
        title.setOpaque(true);
        title.setBackground(DEFAULT_ACCENT_COLOR);
        title.setLayout(null);
        title.setBorder(LINE_BORDER);
        return title;
    }

    /**
     * title text label
     */
    public static JLabel titleTxtLabel (JPanel parent) {
        // TODO: setting up title label
        JLabel title_text = new JLabel("P l a n e S p o t t e r");
        title_text.setFont(TITLE_FONT);
        title_text.setForeground(DEFAULT_FG_COLOR);
        title_text.setFocusable(false);
        title_text.setBounds(parent.getWidth()/2-200, 0, 400, 70); // bounds in Bounds Klasse (?)

        return title_text;
    }

    /**
     * list panel
     */
    public static JPanel listPanel (JDesktopPane parent) {
        // TODO: setting up list panel
        JPanel list = new JPanel();
        list.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        list.setBackground(DEFAULT_BG_COLOR);
        list.setLayout(null);
        list.setBorder(LINE_BORDER);

        return list;
    }

    /**
     * map panel
     */
    public static JPanel mapPanel (JDesktopPane parent) {
        // TODO: setting up map panel
        JPanel map = new JPanel();
        map.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        map.setBackground(DEFAULT_BG_COLOR);
        map.setLayout(null);
        map.setBorder(LINE_BORDER);

        return map;
    }

    /**
     * menu panel
     */
    public static JPanel menuPanel (JDesktopPane parent) {
        // TODO: setting up menu panel
        JPanel menu = new JPanel();
        menu.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        menu.setBackground(DEFAULT_BG_COLOR);
        menu.setLayout(null);

        return menu;
    }

    /**
     * info panel
     */
    public static JPanel infoPanel (JDesktopPane parent) {
        // TODO: setting up info panel
        JPanel info = new JPanel();
        info.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        info.setBackground(DEFAULT_BG_COLOR);

        return info;
    }

    /**
     *
     */
    public static JLabel backgroundLabel () {
        // TODO: setting up background image
        JLabel bground = new JLabel(img);
        bground.setSize(Bounds.MAINPANEL.width, Bounds.MAINPANEL.height);

        return bground;
    }


}
