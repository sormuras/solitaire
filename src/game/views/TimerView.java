/**
 * MIT License
 * 
 * Copyright (c) 2019 Daniel Ricci {@literal <thedanny09@icloud.com>}
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package game.views;

import java.awt.Color;
import java.awt.FlowLayout;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JLabel;

import framework.core.factories.AbstractFactory;
import framework.core.factories.ViewFactory;
import framework.core.mvc.view.PanelView;

public final class TimerView extends PanelView {

    private Timer _timer = new Timer(true);

    private JLabel _label = new JLabel();
    
    private long _time = 0;
    
    private boolean _running;
    
    private boolean _cancelled;
    
    /**
     * Constructs a new instance of this class type
     */
    public TimerView() {
        this.setBackground(Color.WHITE);
        _label.setText(this.toString());
        add(_label);

        // Set the VGap so that the time renders appropriately
        FlowLayout layout = (FlowLayout) this.getLayout();
        layout.setVgap(0);
    }
    
    public void startGameTimer() {
        if(_cancelled || _running || !isVisible()) {
            return;
        }
        
        _running = true;
        _time = 0;        
        _timer.schedule(new TimerTask() {
            @Override public void run() {
                ++_time;
                _label.setText(TimerView.this.toString());
                if(_time % 10 == 0) {
                    AbstractFactory.getFactory(ViewFactory.class).get(ScoreView.class).updateScoreTimerTick();
                }
            }
        }, 1000, 1000);
    }
    
    public long getTime() {
        return _time;
    }
    
    public void stop() {
        _cancelled = true;
        _timer.cancel();
        _running = false;
    }
    
    @Override public void destructor() {
        _cancelled = true;
        _timer.cancel();
        super.destructor();
    }
    
    @Override public String toString() {
        return "Time: " + String.valueOf(_time);
    }
}
