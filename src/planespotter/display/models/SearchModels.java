package planespotter.display.models;

import libs.UWPButton;
import org.jetbrains.annotations.NotNull;
import planespotter.constants.SearchType;
import planespotter.controller.ActionHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static planespotter.constants.GUIConstants.*;
import static planespotter.constants.DefaultColor.*;

/**
 * @name SearchModels
 * @author jml04
 * @version 1.0
 *
 * class SearchModels contains different (gui-menu) search models
 */
public final class SearchModels {

    /**
     * radio buttons
     */
    public JComboBox<String> searchFor_cmbBox(JPanel parent, ItemListener listener) {
        // setting up "search for" combo box
        var searchFor = new JComboBox<>(this.searchBoxItems());
        searchFor.setBounds(parent.getWidth()/2, 10, (parent.getWidth()-20)/2, 25);
        searchFor.setBackground(DEFAULT_SEARCH_ACCENT_COLOR.get());
        searchFor.setForeground(DEFAULT_MAP_ICON_COLOR.get());
        searchFor.setFont(FONT_MENU);
        searchFor.addItemListener(listener);

        return searchFor;
    }

    /**
     * @param parent is the panel where the combo-box is in
     * @return menu combobox-text-label
     */
    public JLabel cmbBoxLabel(JPanel parent) {
        var boxLabel = new JLabel("Search for:");
        boxLabel.setBounds(10, 10, (parent.getWidth()-20)/2, 25);
        boxLabel.setForeground(DEFAULT_MAP_ICON_COLOR.get());
        boxLabel.setFont(FONT_MENU);
        boxLabel.setOpaque(false);

        return boxLabel;
    }

    /**
     * @return search combo-box items (array of Strings)
     */
    private String[] searchBoxItems() {
        return new String[] {
                "Flight",
                "Plane",
                "Airline",
                "Airport",
                "Area"
        };
    }

    /**
     * @return panel for exact search settings
     */
    public JSeparator searchSeperator(JPanel parent) {
        // TODO: setting up exact search panel
        var seperator = new JSeparator(JSeparator.HORIZONTAL);
        seperator.setBounds(10, 43, parent.getWidth()-20, 2);
        seperator.setBackground(DEFAULT_SEARCH_ACCENT_COLOR.get());

        return seperator;
    }

    /**
     * @param parent is the parent panel where the message label is shown in
     * @return the search message text area
     */
    public JTextArea searchMessage(JPanel parent) {
        var message = "Es muss mindestens eins der Felder ausgefüllt sein!";
        var headMessage = new JTextArea(message);
        headMessage.setBounds(10, parent.getHeight()-80, parent.getWidth()-20, 35);
        headMessage.setBackground(DEFAULT_BG_COLOR.get());
        headMessage.setForeground(DEFAULT_FONT_COLOR.get());
        headMessage.setBorder(null);
        headMessage.setEditable(false);
        headMessage.setLineWrap(true);
        headMessage.setWrapStyleWord(true);
        headMessage.setOpaque(false);
        var font = new Font(FONT_MENU.getFontName(), Font.PLAIN, 12);
        headMessage.setFont(font);

        return headMessage;
    }

    public Map<SearchType, List<JComponent>> allSearches(@NotNull SearchPane searchPane, @NotNull ActionHandler actionHandler) {
        Map<SearchType, List<JComponent>> searchModels = new HashMap<>(4);
        searchModels.put(SearchType.FLIGHT, flightSearch(searchPane, actionHandler));
        searchModels.put(SearchType.PLANE, planeSearch(searchPane, actionHandler));
        searchModels.put(SearchType.AIRLINE, airlineSearch(searchPane, actionHandler));
        searchModels.put(SearchType.AIRPORT, airportSearch(searchPane, actionHandler));
        return searchModels;
    }



    /**
     * @param pane is the parent panel component
     * @return list of JLabels (the search field names)
     */
    public List<JComponent> flightSearch(SearchPane pane, ActionHandler listener) {
        var components = new ArrayList<JComponent>();
        components.add(new JLabel("ID:"));
        var id = new JTextField();
        pane.searchFields.put("flight.id", id);
        components.add(id);
        components.add(new JLabel("Callsign.:"));
        var callsign = new JTextField();
        pane.searchFields.put("flight.callsign", callsign);
        components.add(callsign);
        // TODO: 09.08.2022 FlightNr

        var loadList = new UWPButton();
        loadList.setText("Load List");
        components.add(loadList);
        var loadMap = new UWPButton();
        loadMap.setText("Load Map");
        components.add(loadMap);
        int width = (pane.getWidth()-20)/2;
        int y = 55;
        for (var c : components) {
            if (c instanceof JLabel) {
                c.setBounds(10, y, width, 25);
                c.setBackground(DEFAULT_BG_COLOR.get());
                c.setForeground(DEFAULT_MAP_ICON_COLOR.get());
                c.setOpaque(false);
            } else if (c instanceof JTextField) {
                c.setBounds(pane.getWidth()/2, y, width, 25);
                c.setBackground(DEFAULT_FONT_COLOR.get());
                c.setForeground(DEFAULT_FG_COLOR.get());
                c.setBorder(BorderFactory.createLineBorder(DEFAULT_SEARCH_ACCENT_COLOR.get()));
                c.addKeyListener(listener);
                y += 35;
            } else if (c instanceof JButton bt) {
                var buttonText = bt.getText();
                if (buttonText.equals("Load List")) {
                    bt.setBounds(10, pane.getHeight()-35, width-5, 25);
                    bt.setName("loadList");
                } else if (buttonText.equals("Load Map")) {
                    bt.setBounds((pane.getWidth()/2)+5, pane.getHeight()-35, width-5, 25);
                    bt.setName("loadMap");
                }
                bt.setBackground(DEFAULT_SEARCH_ACCENT_COLOR.get());
                bt.setForeground(DEFAULT_FONT_COLOR.get());
                bt.setBorder(MENU_BORDER);
                bt.addActionListener(listener);
            }
            c.setFont(FONT_MENU);
            c.setVisible(false);
        }
        return components;
    }

    /**
     * @param pane is the parent panel component
     * @return list of JLabels (the search field names)
     */
    public List<JComponent> planeSearch(SearchPane pane, ActionHandler listener) {
        var components = new ArrayList<JComponent>();
        components.add(new JLabel("ID:"));
        var id = new JTextField();
        pane.searchFields.put("plane.id", id);
        components.add(id);
        components.add(new JLabel("Planetype:"));
        var planetype = new JTextField();
        pane.searchFields.put("plane.type", planetype);
        components.add(planetype);
        components.add(new JLabel("ICAO:"));
        var icao = new JTextField();
        pane.searchFields.put("plane.icao", icao);
        components.add(icao);
        components.add(new JLabel("Tail-Nr.:"));
        var tailNr = new JTextField();
        pane.searchFields.put("plane.tailnr", tailNr);
        components.add(tailNr);
        var loadList = new UWPButton();
        loadList.setText("Load List");
        components.add(loadList);
        var loadMap = new UWPButton();
        loadMap.setText("Load Map");
        components.add(loadMap);
        int width = (pane.getWidth()-20)/2;
        int y = 55;
        for (var c : components) {
            if (c instanceof JLabel) {
                c.setBounds(10, y, width, 25);
                c.setBackground(DEFAULT_BG_COLOR.get());
                c.setForeground(DEFAULT_MAP_ICON_COLOR.get());
                c.setOpaque(false);
            } else if (c instanceof JTextField) {
                c.setBounds(pane.getWidth()/2, y, width, 25);
                c.setBackground(DEFAULT_FONT_COLOR.get());
                c.setForeground(DEFAULT_FG_COLOR.get());
                c.setBorder(BorderFactory.createLineBorder(DEFAULT_SEARCH_ACCENT_COLOR.get()));
                c.addKeyListener(listener);
                y += 35;
            } else if (c instanceof JButton bt) {
                var buttonText = bt.getText();
                if (buttonText.equals("Load List")) {
                    bt.setBounds(10, pane.getHeight()-35, width-5, 25);
                    bt.setName("loadList");
                } else if (buttonText.equals("Load Map")) {
                    bt.setBounds((pane.getWidth()/2)+5, pane.getHeight()-35, width-5, 25);
                    bt.setName("loadMap");
                }
                bt.setBackground(DEFAULT_SEARCH_ACCENT_COLOR.get());
                bt.setForeground(DEFAULT_FONT_COLOR.get());
                bt.setBorder(MENU_BORDER);
                bt.addActionListener(listener);
            }
            c.setFont(FONT_MENU);
            c.setVisible(false);
        }
        return components;
    }

    public ArrayList<JComponent> airportSearch(SearchPane pane, ActionHandler listener) {
        var components = new ArrayList<JComponent>();
        components.add(new JLabel("ID:"));
        var id = new JTextField();
        pane.searchFields.put("airport.id", id);
        components.add(id);
        components.add(new JLabel("Tag:"));
        var tag = new JTextField();
        pane.searchFields.put("airport.tag", tag);
        components.add(tag);
        components.add(new JLabel("Name:"));
        var name = new JTextField();
        pane.searchFields.put("airport.name", name);
        components.add(name);
        var loadList = new UWPButton();
        loadList.setText("Load List");
        components.add(loadList);
        var loadMap = new UWPButton();
        loadMap.setText("Load Map");
        components.add(loadMap);
        int width = (pane.getWidth()-20)/2;
        int y = 55;
        for (var c : components) {
            if (c instanceof JLabel) {
                c.setBounds(10, y, width, 25);
                c.setBackground(DEFAULT_BG_COLOR.get());
                c.setForeground(DEFAULT_MAP_ICON_COLOR.get());
                c.setOpaque(false);
            } else if (c instanceof JTextField) {
                c.setBounds(pane.getWidth()/2, y, width, 25);
                c.setBackground(DEFAULT_FONT_COLOR.get());
                c.setForeground(DEFAULT_FG_COLOR.get());
                c.setBorder(BorderFactory.createLineBorder(DEFAULT_SEARCH_ACCENT_COLOR.get()));
                c.addKeyListener(listener);
                y += 35;
            } else if (c instanceof JButton bt) {
                var buttonText = bt.getText();
                if (buttonText.equals("Load List")) {
                    bt.setBounds(10, pane.getHeight()-35, width-5, 25);
                    bt.setName("loadList");
                } else if (buttonText.equals("Load Map")) {
                    bt.setBounds((pane.getWidth()/2)+5, pane.getHeight()-35, width-5, 25);
                    bt.setName("loadMap");
                }
                bt.setBackground(DEFAULT_SEARCH_ACCENT_COLOR.get());
                bt.setForeground(DEFAULT_FONT_COLOR.get());
                bt.setBorder(MENU_BORDER);
                bt.addActionListener(listener);
            }
            c.setFont(FONT_MENU);
            c.setVisible(false);
        }
        return components;
    }

    public List<JComponent> airlineSearch(@NotNull SearchPane pane, @NotNull ActionHandler listener) {
        var components = new ArrayList<JComponent>();
        components.add(new JLabel("ID:"));
        var id = new JTextField();
        pane.searchFields.put("airline.id", id);
        components.add(id);
        components.add(new JLabel("Tag:"));
        var tag = new JTextField();
        pane.searchFields.put("airline.tag", tag);
        components.add(tag);
        components.add(new JLabel("Name:"));
        var name = new JTextField();
        pane.searchFields.put("airline.name", name);
        components.add(name);
        components.add(new JLabel("Country:"));
        var country = new JTextField();
        pane.searchFields.put("airline.country", country);
        components.add(country);
        var loadList = new UWPButton();
        loadList.setText("Load List");
        components.add(loadList);
        var loadMap = new UWPButton();
        loadMap.setText("Load Map");
        components.add(loadMap);
        int width = (pane.getWidth()-20)/2;
        int y = 55;
        for (var c : components) {
            if (c instanceof JLabel) {
                c.setBounds(10, y, width, 25);
                c.setBackground(DEFAULT_BG_COLOR.get());
                c.setForeground(DEFAULT_MAP_ICON_COLOR.get());
                c.setOpaque(false);
            } else if (c instanceof JTextField) {
                c.setBounds(pane.getWidth()/2, y, width, 25);
                c.setBackground(DEFAULT_FONT_COLOR.get());
                c.setForeground(DEFAULT_FG_COLOR.get());
                c.setBorder(BorderFactory.createLineBorder(DEFAULT_SEARCH_ACCENT_COLOR.get()));
                c.addKeyListener(listener);
                y += 35;
            } else if (c instanceof JButton bt) {
                var buttonText = bt.getText();
                if (buttonText.equals("Load List")) {
                    bt.setBounds(10, pane.getHeight()-35, width-5, 25);
                    bt.setName("loadList");
                } else if (buttonText.equals("Load Map")) {
                    bt.setBounds((pane.getWidth()/2)+5, pane.getHeight()-35, width-5, 25);
                    bt.setName("loadMap");
                }
                bt.setBackground(DEFAULT_SEARCH_ACCENT_COLOR.get());
                bt.setForeground(DEFAULT_FONT_COLOR.get());
                bt.setBorder(MENU_BORDER);
                bt.addActionListener(listener);
            }
            c.setFont(FONT_MENU);
            c.setVisible(false);
        }
        return components;
    }

}
