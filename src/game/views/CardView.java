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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

import javax.swing.BoxLayout;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;

import framework.api.IView;
import framework.communication.internal.signal.ISignalReceiver;
import framework.communication.internal.signal.arguments.EventArgs;
import framework.core.factories.AbstractFactory;
import framework.core.factories.ControllerFactory;
import framework.core.factories.ViewFactory;
import framework.core.graphics.IRenderable;
import framework.core.mvc.view.PanelView;
import framework.core.mvc.view.layout.DragListener;
import framework.core.physics.CollisionListener;
import framework.core.physics.ICollidable;
import framework.utils.MouseListenerEvent;
import framework.utils.MouseListenerEvent.SupportedActions;
import framework.utils.logging.Tracelog;

import game.config.OptionsPreferences;
import game.config.OptionsPreferences.DrawOption;
import game.controllers.CardController;
import game.controllers.MovementRecorderController;
import game.models.CardModel;
import game.views.helpers.ViewHelper;

/**
 * This view represents the representation of a single card
 * 
 * @author Daniel Ricci {@literal <thedanny09@icloud.com>}
 *
 */
public final class CardView extends PanelView implements ICollidable {

    /**
     * The card selection events for this proxy view
     * 
     * @author Daniel Ricci {@literal <thedanny09@icloud.com>}
     *
     */
    private final class CardSelectionEvents extends MouseListenerEvent {
        
        /**
         * The parent layer pane
         */
        private JLayeredPane _parentLayeredPane;
        
        /**
         * The parent panel view
         */
        private IView _parentPanelView;
        
        /**
         * Constructs a new instance of this class type
         */
        public CardSelectionEvents() {
            super(SupportedActions.LEFT);
        }
        
        @Override public void mousePressed(MouseEvent event) {

            super.mousePressed(event);
            if(getIsConsumed() && event.isConsumed()) {
                return;
            }
            
            if(!isEnabled()) {
                return;
            }
            
            // Get the parent of this card view, used as a reference to go back to whatever we were coming from
            _parentLayeredPane = (JLayeredPane) CardView.this.getParent();
            
            // Get the parent IView w.r.t this card view, this is used to make sure that we are not trying to collide with ourselves
            _parentPanelView = CardView.this.getParentIView();

            // Get the list of components that the parent owns
            Component[] components =_parentLayeredPane.getComponents();

            // Find the card that is being dragged and take all the siblings below it and populate them into the
            // layered pane composed by the CardView.this reference
            mainLabel : for(int i = components.length - 1; i >= 0; --i) {

                if(components[i].equals(CardView.this)) {

                    // Get the siblings of cards within the components list (excluding CardView.this)
                    List<CardView> cardViews = Arrays.asList(Arrays.copyOfRange(components, 0, i, CardView[].class));

                    // Reverse the list because layered panes associate objects closer to layer 0 as being closer to the screen.
                    Collections.reverse(cardViews);

                    // Fixes a bug where the layered pane is chopped from the waist down
                    CardView.this.layeredPane.setSize(layeredPane.getWidth(), layeredPane.getHeight() + (cardViews.size() * 15));
                    
                    // For each sibling add it into the associated layere pane and position it correctly within
                    // the pane, accounting for the fact that CardView.this is the temporary 'root'
                    for(int j = 0; j < cardViews.size(); ++j) {
                        layeredPane.add(cardViews.get(j));
                        layeredPane.setLayer(cardViews.get(j), j);
                        
                        // Account for the border offsets of -1 for left and bottom
                        cardViews.get(j).setBounds(new Rectangle(0, (15 * (j + 1)), cardViews.get(j).getPreferredSize().width, cardViews.get(j).getPreferredSize().height));
                        _parentLayeredPane.remove(cardViews.get(j));
                    }

                    // Position the card at the same place where the drag was attempted from, because when you
                    // add to the application it will position the component at the origin which is not desired
                    Point initialLocation = CardView.this.getLocation();
                    
                    // Note: Add +2 to the width and height because of the initial border size offset
                    CardView.this.setBounds(new Rectangle(_parentLayeredPane.getParent().getLocation().x + initialLocation.x, _parentLayeredPane.getParent().getLocation().y + initialLocation.y, layeredPane.getWidth(), layeredPane.getHeight()));

                    // Remove the card view reference from it's initial parent
                    _parentLayeredPane.remove(CardView.this);
                    
                    // Get a reference to the game view and status view, and add the card into the proper
                    // z-order so that it appears underneath the status bar, but over everything else in the game
                    ViewFactory viewFactory = AbstractFactory.getFactory(ViewFactory.class);
                    GameView gameView = viewFactory.get(GameView.class);
                    StatusBarView statusBarView = viewFactory.get(StatusBarView.class);

                    gameView.add(CardView.this, gameView.getComponentZOrder(statusBarView) + 1);
                    gameView.repaint();

                    break mainLabel;
                }
            }
        }

        @Override public void mouseReleased(MouseEvent event) {
            
            super.mouseReleased(event);
            if(getIsConsumed() && event.isConsumed()) {
                return;
            }
            
            if(!isEnabled()) {
                return;
            }
            
            if(_parentLayeredPane == null) {
                _parentPanelView = null;
                return;
            }
            
            // If there is a valid collider, set that as the new parent
            if(_collisionListener.getCollision() != null) {
                AbstractPileView pileView = (AbstractPileView) _collisionListener.getCollision();
                
                // Do not go further if what we have collided with is where we were already, that is not considered a valid collision.
                // Note: Should this go inside of the drag listener?
                if(!pileView.equals(_parentPanelView)) { 
                    // Get the before movement type to know where the move is coming from
                    Optional<Component> layeredPane = Arrays.asList(pileView.getComponents()).stream().filter(z -> z.getClass() == JLayeredPane.class).findFirst();
                    if(layeredPane.isPresent()) {
                        AbstractFactory.getFactory(ControllerFactory.class).get(MovementRecorderController.class).recordMovement((IUndoable)_parentLayeredPane.getParent(), pileView);                  
                        _parentLayeredPane = (JLayeredPane) layeredPane.get();
                    }
                    else {
                        Tracelog.log(Level.SEVERE, true, "Could not find JLayeredPane within the CardView mouseReleased event...");
                        return;
                    }
                }
            }
            
            // Get the offset that was set, and use this within our calculations
            AbstractPileView parent = (AbstractPileView) _parentLayeredPane.getParent();
            parent.addCard(CardView.this);
            _parentLayeredPane = null;
            _parentPanelView = null;
        }
    }
    
    /**
     * This mouse adapter handles events when the card is pressed with the mouse
     */
    private MouseListenerEvent _mouseActionListener = new MouseListenerEvent(SupportedActions.LEFT) {
        @Override public void mousePressed(MouseEvent event) {
            super.mousePressed(event);
            if(getIsConsumed() && event.isConsumed()) {
                return;
            }
            
            if(!isEnabled()) {
                return;
            }

            if(CardView.this.getParent().getComponents()[0].equals(CardView.this)) {
                if(event.getClickCount() == 1) {
                    uncoverBackside(false);
                }
                else {
                    performCardAutoMovement();
                }
            }
        }
    };
    
    /**
     * The preferred width of this card
     */
    public static final int CARD_WIDTH = 71;

    /**
     * The preferred height of this card
     */
    public static final int CARD_HEIGHT = 100;

    /**
     * The controller associated to this card view
     */
    private CardController _controller;

    /**
     * The draggable listener associated to this view
     */
    private final DragListener _dragListener = new DragListener(SupportedActions.LEFT);

    /**
     * The collision listener associated to this view
     */
    private final CollisionListener _collisionListener = new CollisionListener(this, SupportedActions.LEFT);

    /**
     * The layered pane that holds the potential list of cards that would be dragged along-side this card vuew
     */
    protected final JLayeredPane layeredPane = new JLayeredPane();
   
    /**
     * Indicates if selections are enabled
     */
    private boolean _highlightsEnabled;
    
    /**
     * The card selection events associated to this card view
     */
    private final CardSelectionEvents _cardSelectionEvents = new CardSelectionEvents();
    
    /**
     * The card proxy associated to this view
     */
    private CardProxyView _cardProxy;
    
    /**
     * Signal indicating that this view should synchronizr with the outline option
     */
    public final static String EVENT_OUTLINE_SYNCHRONIZE = "EVENT_OUTLINE_SYNCHRONIZE";
    
    private CardView() {
        getViewProperties().setShouldAlwaysRedraw(true);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setOpaque(false);
        setBackground(Color.BLACK);
        
        add(layeredPane);
    }
    
    /**
     * Constructs a new instance of this class type
     * 
     * @param cardModel The cards model underlying this card view
     */
    public CardView(CardModel cardModel) {
        this();
        
        cardModel.addListener(this);
        _controller = new CardController(cardModel);
        getViewProperties().setEntity(_controller);   

        // Create the card proxy view and render it
        _cardProxy = new CardProxyView(this);
        _cardProxy.render();
        
        addMouseListener(_dragListener);
        addMouseMotionListener(_dragListener);
        
        addMouseListener(_collisionListener);
        addMouseMotionListener(_collisionListener);
        
        // Add the mouse listener responsible for handling single clicks and double clicks on this card.
        // Note: This will sometimes not be called depending on if the proxy is enabled or not, since the 
        //       proxy sits on top of this card. However, when the backside is being shown, this would indeed
        //       be called, however the double click will not be called since the single click will
        //       initiate the proxy, thus the double click of the proxy will be called
        //
        // Note: This mouse listener should be before any other mouse listener within this class
        addMouseListener(_mouseActionListener);

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if(!SwingUtilities.isRightMouseButton(event)) {
                    AbstractFactory.getFactory(ViewFactory.class).get(TimerView.class).startGameTimer();
                    removeMouseListener(this);
                }
            }
        });
        
        // Register this view and it's underlying proxy to perform auto moves
        ViewHelper.registerForCardsAutocomplete(this);
        ViewHelper.registerForCardsAutocomplete(_cardProxy);
        
        // Register this view to handle the events raised by the card selection events
        addMouseListener(_cardSelectionEvents);
        addMouseMotionListener(_cardSelectionEvents);
        
        // Listen in on events when we need to synchronize withthe online option
        addSignal(EVENT_OUTLINE_SYNCHRONIZE, new ISignalReceiver<EventArgs>() {
            @Override public void signalReceived(EventArgs event) {
                synchronizeWithOptions();

                // When playing in draw three, make sure that cards that are not top-most are not enabled. This
                // needs to be done after the synchronize.
                OptionsPreferences optionsPreferences = new OptionsPreferences();
                optionsPreferences.load();
                if(optionsPreferences.drawOption == DrawOption.THREE && CardView.this.getParentIView().getClass() == TalonPileView.class) {
                    if(((JLayeredPane)getParent()).getPosition(CardView.this) > 0) {
                        _dragListener.setEnabled(false);
                        _collisionListener.setEnabled(false);
                        _cardSelectionEvents.setEnabled(false);
                    }
                }
            }
        });       
        synchronizeWithOptions();
    }
    
    public static CardView createLightWeightCard(CardView cardView) {
        CardView newCardView = new CardView();
        newCardView.setEnabled(cardView.isEnabled());
        cardView.getRenderableContent().forEach(z -> newCardView.addRenderableContent(z));
        return newCardView;
    }
    
    /**
     * Synchronizes this card view w.r.t the current outline options that are set within the game.
     */
    private void synchronizeWithOptions() {
        // Verify if the option for highlighting is enabled or not
        OptionsPreferences optionsPreferences = new OptionsPreferences();
        optionsPreferences.load();
        _highlightsEnabled = optionsPreferences.outlineDragging;

        // If the card has its backside shown or the outline option is enabled
        // then do not allow dragging or collision to work as normal
        boolean isAvailable = !_controller.getCard().getIsBackside() && !optionsPreferences.outlineDragging;
        _dragListener.setEnabled(isAvailable);
        _collisionListener.setEnabled(isAvailable);
        
        // If the backside is not being shown, then add the event handler for card drag event
        // Note: In the event that the options preferences calls for outline mode, the entire
        //       operation of performing a click-down, click-up, should be done by the proxy and
        //       not this card explicitely.
        if(!_controller.getCard().getIsBackside()) {
            if(!optionsPreferences.outlineDragging) {
                remove(_cardProxy);
            }
            else {
                add(_cardProxy);
            }
        } 
        
        // The selection events associated to this view should only be enabled if the
        // backside is not shown and the outline mode is not enabled
        _cardSelectionEvents.setEnabled(!_controller.getCard().getIsBackside() && !optionsPreferences.outlineDragging);
    }
    
    /**
     * @return The card proxy associated to this view
     */
    public CardProxyView getProxyView() {
        return _cardProxy;
    }
    
    /**
     * Attempts to uncover the backside of this view
     * 
     * @param forceBackside TRUE if the backside should be force uncovered, FALSE otherwise
     */
    public void uncoverBackside(boolean forceBackside) {
        if(_controller.getCard().getIsBackside()) {
            _controller.getCard().setBackside(false);
            _controller.getCard().refresh();
            
            // Record the movement
            if(!forceBackside) {
                AbstractFactory.getFactory(ControllerFactory.class).get(MovementRecorderController.class).recordMovement((IUndoable)CardView.this.getParentIView(), null);
            }

            // Only allow this card view to have dragging and collision working `vanilla`
            // style if the outline option is not selected
            OptionsPreferences preferences = new OptionsPreferences();
            preferences.load();
            if(!preferences.outlineDragging) {
                _dragListener.setEnabled(true);
                _collisionListener.setEnabled(true);
                _cardSelectionEvents.setEnabled(true);
            }
            
            if(preferences.outlineDragging) {
                add(_cardProxy);
                _cardProxy.setVisible(true);
            }
            
            repaint();
        }
    }
    
    /**
     * @return TRUE of the backside is showing, FALSE otherwise
     */
    public boolean isBacksideShowing() {
        return _controller.getCard().getIsBackside();
    }
    
    /**
     * Performs an auto card movement, attempting to move this card to the foundation
     * 
     * @return TRUE if the auto movement was successfull, FALSE otherwise
     */
    public boolean performCardAutoMovement() {
        return performCardAutoMovement(false);
    }

    /**
     * Performs an auto card movement, attempting to move this card to the foundation
     * 
     * @param forcefully If the movement is forcefully being done (when the user cheats to win the game)
     * 
     * @return TRUE if the auto movement was successfull, FALSE otherwise
     */
    public boolean performCardAutoMovement(boolean forcefully) {
        if(!_controller.getCard().getIsBackside() && !(getParentIView() instanceof FoundationPileView)) {
            
            // Make sure that we are not double clicking on an ACE. That doesn't make much sense here in this case
            if(AbstractFactory.getFactory(ViewFactory.class).getAll(FoundationPileView.class).stream().anyMatch(z -> z.layeredPane.getComponentCount() == 1 && z.layeredPane.getComponents()[0] == CardView.this)) {
                return false;
            }
            
            // Get the list of foundation views. 
            List<FoundationPileView> foundationViews = AbstractFactory.getFactory(ViewFactory.class).getAll(FoundationPileView.class);
            
            // Reverse the list, so that the card populating the left-most foundation view, this just looks a lot better
            Collections.reverse(foundationViews);
            
            // Go through the list of foundation views and see if there is a match
            for(FoundationPileView foundationView : foundationViews) {
                if(foundationView.isValidCollision(CardView.this)) {
                    
                    if(!forcefully) {
                        // Record the fact that a movement occurred 
                        AbstractFactory.getFactory(ControllerFactory.class).get(MovementRecorderController.class).recordMovement((AbstractPileView)CardView.this.getParentIView(), foundationView);
                    }
                    
                    // Stop the current drag listener of this card from doing anything, so that things
                    // like drag will stop being processed
                    _dragListener.stopDragEvent();
                    
                    // Add to the layered pane destination
                    foundationView.addCard(CardView.this);
                                        
                    // Repaint the components
                    layeredPane.repaint();
                    foundationView.repaint();
                   
                    // Repaint the game view, this will fix a rendering bug where in outline mode, the status bar and
                    // the tableau view would not render properly. Look at bug #128.
                    AbstractFactory.getFactory(ViewFactory.class).get(GameView.class).repaint();
                    
                    return true;
                }
            }                            
        }
        
        return false;
    }
    
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        
        // Propagate the state change to the proxy. Ensure that no circular reference could ever occur
        if(_cardProxy != null && _cardProxy.isEnabled() != enabled) {
            _cardProxy.setEnabled(enabled);
        }

        _dragListener.setEnabled(enabled);
        _collisionListener.setEnabled(enabled);
    }
    
    @Override public void preProcessGraphics(IRenderable renderableData, Graphics context) {        
        super.preProcessGraphics(renderableData, context);
        if(_highlightsEnabled && getIsHighlighted()) {
            context.setXORMode(Color.WHITE);
        }
    }
    
    @Override public void removeAll() {
        super.removeAll();
        _dragListener.setEnabled(false);
        _collisionListener.setEnabled(false);
    }

    @Override public void render() {
        super.render();
        extents.x = 0;
        extents.y = 0;
        extents.width = this.getPreferredSize().width;
        extents.height = this.getPreferredSize().height;
        
        if(_controller != null) {
            _controller.refresh();
        }
    }

    @Override public void update(EventArgs event) {
        super.update(event);
        addRenderableContent((IRenderable)event.getSource()); 
        repaint();
    }
    
    @Override public void setBounds(int x, int y, int width, int height) {
        // For now, this is here to fix a bug where the timer updating on the status bar will
        // cause a revalidation of the gridbag that the game view has, causing the card that is dragging
        // to be reset to the `origin`.
        if(y == 5) {
            return;
        }

        super.setBounds(x, y, width, height);
    }
        
    @Override public String toString() {
        // Hold onto the layer position
        int layer = JLayeredPane.getLayer(this);
        
        // Attempt to get the position within the layer.
        int positionWithinLayer = -1;
        if(this.getParent() instanceof JLayeredPane) {
            JLayeredPane parentLayeredPane = (JLayeredPane) this.getParent();
            List<Component> components = Arrays.asList(parentLayeredPane.getComponentsInLayer(layer));
            positionWithinLayer = components.indexOf(CardView.this);
        }
        return String.format("%s%s%s\t[%d][%d]",
            isVisible() ? "[V]" : "[H]",
            isEnabled() ? "[E]" : "[D]",
            getViewProperties().getEntity(CardController.class).getCard(),
            layer,
            positionWithinLayer
        );
    }
    
    @Override public boolean isValidCollision(Component source) {
        IView view = (IView)source;

        // A card is coming into this card, and we are on the foundation view
        if(getParent().getParent() instanceof FoundationPileView) {
            CardController thisCardViewController = view.getViewProperties().getEntity(CardController.class);
            return _controller.isValidFoundationMove(thisCardViewController.getCard());            
        }
        // The card is coming onto this card which is on the pile view (should be, there are only two options for this game)
        else {
            CardController cardViewController = this.getViewProperties().getEntity(CardController.class);
            return cardViewController.getCard().isCardBeforeAndOppositeSuite(view.getViewProperties().getEntity(CardController.class).getCard());
        }
    }

    @Override public void onCollisionStart(Component source) {
        // Set the opaque to true to color in the card. It is originally false so 
        // that the corners with are transparent will not be shown, since setting this
        // to true forces the card dimensions to show something.
        this.setOpaque(true);
    }

    @Override public void onCollisionStop(Component source) {
        // Set the opaque back to false which is the default for all cards
        this.setOpaque(false);
    }
}