import javax.swing.*;
import java.awt.event.*;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class MyMouseListener implements MouseListener{
    public void mouseClicked(MouseEvent e) {
        JButton button = (JButton) e.getSource();
        String text = "<html><b>" + button.getText()
                + " mouseReleased() <br>" + button.getText()
                + " mouseClicked() </b><html>";
        System.out.println(text);
    }

    public void mouseEntered(MouseEvent e) {
        JButton button = (JButton) e.getSource();
        System.out.println(button.getText() + " mouseEntered()");
    }

    public void mouseExited(MouseEvent e) {
        JButton button = (JButton) e.getSource();
        System.out.println(button.getText() + " mouseExited()");
    }

    public void mousePressed(MouseEvent e) {
        JButton button = (JButton) e.getSource();
        System.out.println(button.getText() + " mousePressed()");
    }

    public void mouseReleased(MouseEvent e) {
        JButton button = (JButton) e.getSource();
        System.out.println(button.getText() + " mouseReleased()");
    }
}
