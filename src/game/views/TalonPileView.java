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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;

import framework.core.factories.AbstractFactory;
import framework.core.factories.ControllerFactory;
import framework.core.factories.ViewFactory;
import framework.core.mvc.view.PanelView;
import framework.core.physics.ICollidable;
import framework.utils.MouseListenerEvent;
import framework.utils.MouseListenerEvent.SupportedActions;
import framework.utils.logging.Tracelog;

import game.config.OptionsPreferences;
import game.config.OptionsPreferences.DrawOption;
import game.config.OptionsPreferences.ScoringOption;
import game.controllers.MovementRecorderController;
import game.models.CardModel;
import game.views.helpers.ViewHelper;

/**
 * This views represents the talon pile view. This view will display cards whenever the user clicks on
 * the Stock view. This view will adapt itself based on the options set (draw three vs. draw one)
 * 
 * @author Daniel Ricci {@literal <thedanny09@icloud.com>}
 */
public final class TalonPileView extends AbstractPileView implements ICollidable {

    /**
     * Specifies the offset of each card within this view
     */
    private int CARD_OFFSET_X = 12;
    
    /**
     * The available states of the talon
     * 
     * @author Daniel Ricci {@literal <thedanny09@icloud.com>}
     */
    public enum TalonCardState {
        // An Empty Deck
        EMPTY,
        // The Deck has been played through
        DECK_PLAYED,
        // Normal card played
        NORMAL
    }

    /**
     * A helper class for holding onto a layer and it's associated card state
     * 
     * @author Daniel Ricci {@literal <thedanny09@icloud.com>}
     */
    private class TalonCardReference {
        
        /**
         * The layer of the last card that was clicked
         */
        public final int layer;
        
        /**
         * The last card that was clicked w.r.t this view
         */
        public final CardView card;
        
        /**
         * Constructs a new instance of this class type
         *
         * @param card A card view
         * @param layer A specified layer
         */
        public TalonCardReference(CardView card, int layer) {
            this.card = card;
            this.layer = layer;
        }
        
        /**
         * Constructs a new instance of this class type
         *
         * @param card A card view
         */
        public TalonCardReference(CardView card) {
            this.card = card;
            this.layer = JLayeredPane.getLayer(card);
        }
        
        @Override public String toString() {
            return String.format("Layer: %s | Card: %s", this.layer, this.card);
        }
    }
    
    /**
     * The total number of cards that this view contains by default
     */
    public static final int TOTAL_CARD_SIZE = 24;
    
    /**
     * The last card hand state of this talon
     */
    private TalonCardState _lastCardHandState = null;
    
    /**
     * The number of times that the deck was played
     */
    private int _deckPlays;
    
    /**
     * This flag indicates if the deck is in a recycled state
     */
    private boolean _isDeckInRecycledState;
    
    /**
     * The blank card associated to the talon view
     */
    private final PanelView _blankCard = new PanelView();

    /**
     * The last card that was interacted with
     */
    private TalonCardReference _lastCardInteracted = null;
    
    /**
     * The card that can be undone
     */
    private TalonCardReference _undoableCard = null;
    
    /**
     * Constructs a new instance of this class type
     */
    private TalonPileView() {
        _blankCard.setBackground(new Color(0, 128, 0));
        // Arbitrary number, big enough to do some damage
        _blankCard.setPreferredSize(new Dimension(1000, 1000));
        _blankCard.setBounds(new Rectangle(0, 0, _blankCard.getPreferredSize().width, _blankCard.getPreferredSize().height));
        _blankCard.setVisible(true);
        
        OptionsPreferences preferences = new OptionsPreferences();
        preferences.load();
        if(preferences.drawOption == DrawOption.THREE) {
            CARD_OFFSET_X = 12;
        }
        else {
            CARD_OFFSET_X = 0;    
        }
        
        // The blank card will always be in this view, so right clicking on it should autocomplete
        // whatever is on the board
        ViewHelper.registerForCardsAutocomplete(_blankCard);
        
        // Add a listener to the blank card since it is sitting above the board. If someone tries to click in this area
        // the timer will start, unknowing to the player that they really clicked on a special area of the board
        _blankCard.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if(!SwingUtilities.isRightMouseButton(event)) {
                    AbstractFactory.getFactory(ViewFactory.class).get(TimerView.class).startGameTimer();
                    _blankCard.removeMouseListener(this);
                }
            }
        });
    }
    
    /**
     * Constructs a new instance of this class type
     * 
     * @param cards The card models to load within this view
     */
    public TalonPileView(List<CardModel> cards) {
        this();
        
        if(cards.size() > TOTAL_CARD_SIZE) {
            Tracelog.log(Level.SEVERE, true, "Talon has been allocated more than the currently set max card size that can be allocated!");
        }
        
        OptionsPreferences preferences = new OptionsPreferences();
        preferences.load();
        
        for(int i = 0, layer = 0; i < cards.size(); ++i) {
            CardView cardView = AbstractFactory.getFactory(ViewFactory.class).add(new CardView(cards.get(i)));
            MouseListenerEvent adapter = new MouseListenerEvent(SupportedActions.LEFT) {
                @Override public void mousePressed(MouseEvent event) {
                    
                    super.mousePressed(event);
                    if(event.isConsumed() && getIsConsumed()) {
                        return;
                    }
                    
                    // Do not allow non-enabled cards to run
                    if(!cardView.isEnabled()) {
                        return;
                    }
                    
                    // Take the card that was pressed on and record it's layer location 
                    _lastCardInteracted = new TalonCardReference(cardView);
                }
                @Override public void mouseReleased(MouseEvent event) {
            
                    super.mouseReleased(event);
                    if(event.isConsumed() && getIsConsumed()) {
                        return;
                    }
                    
                    // Prevent other released events from being called by other cards that are not yet enabled
                    if(!cardView.isEnabled()) {
                        return;
                    }
                   
                    // If the card is no longer associated to the talon then attempt to get the next one
                    if(!(cardView.getParentIView() instanceof TalonPileView)) {
                        
                        // The top-most card cannot be the layered pane
                        boolean cond1 = layeredPane.highestLayer() != JLayeredPane.getLayer(_blankCard);
                        
                        // There must not be any more visible cards (excluding the blank card)
                        boolean cond2 = Arrays.asList(layeredPane.getComponents()).stream().anyMatch(z -> !z.equals(_blankCard) && z.isVisible());
                        
                        if(cond1 && !cond2) {
                            for(int iterations = 0, layerId = JLayeredPane.getLayer(_blankCard) + 1; layerId <= layeredPane.highestLayer() || iterations < 3; ++layerId, ++iterations) {
                                Component component = layeredPane.getComponentsInLayer(layerId)[0];
                                component.setVisible(true);
                            }
                        }
                    }
                    // The card was put back, so position it accordingly so that it can be shown again
                    // Make sure that the card is enabled. Since when a card is not enabled, the event
                    // handlers are not applied to the card
                    else if(cardView.isEnabled()){
                        // If the blank card is on the same layer as this card, put this card to the next layer above. 
                        // This could only occur if this was already top-most
                        if(JLayeredPane.getLayer(_blankCard) == JLayeredPane.getLayer(cardView)) {
                            layeredPane.setLayer(cardView, JLayeredPane.getLayer(cardView) + 1);
                        }
                        setBounds(cardView);
                    }
                    
                    // When the mouse is released, ensure that the component located at the highest layer is enabled
                    layeredPane.getComponentsInLayer(layeredPane.highestLayer())[0].setEnabled(true);
                }
            };
            cardView.addMouseListener(adapter);
            cardView.getProxyView().addMouseListener(adapter);
            
            // Set the default bounds of the card
            cardView.setBounds(new Rectangle(0, 0, cardView.getPreferredSize().width, cardView.getPreferredSize().height));
            
            // All cards are disabled by default, and should be disabled by default after a subsequent deck has been played through
            cardView.setEnabled(false);
            
            // Add the card to the layered pane and set it's layer accordingly
            layeredPane.add(cardView);
            
            if(preferences.drawOption == DrawOption.THREE) {
                layeredPane.setLayer(cardView, layer / 3);
                ++layer;
            }
            else {
                layeredPane.setLayer(cardView, i);
            }
        }
        
        // Add the blank card last, and make sure that it has the topmost layer
        layeredPane.add(_blankCard);
        layeredPane.setLayer(_blankCard, layeredPane.highestLayer() + 1);
    }
    
    /**
     * @return TRUE if the pile style has not yet gone through 4 cards, FALSE otherwise
     */
    public boolean isPhaseOne() {
        return getBlankCardPosition() < 4 - getCardsRemainingCount();
    }
    
    /**
     * @return TRUE if the pile style has not yet gone through 14 cards, FALSE otherwise
     */
    public boolean isPhaseTwo() {
        return getBlankCardPosition() < 14 - getCardsRemainingCount();
    }
    
    /**
     * @return TRUE if the Talon has been played through fully, FALSE otherwise
     */
    public boolean isDeckPlayed() {
        return layeredPane.lowestLayer() == JLayeredPane.getLayer(_blankCard);
    }
    
    /**
     * @return TRUE if the Talon has gone through the specified number 
     *         of deck shuffles (based on the options preferences currently set), FALSE otherwise
     */
    public boolean isTalonEnded() {
        OptionsPreferences preferences = new OptionsPreferences();
        preferences.load();
        
        if(preferences.drawOption == DrawOption.ONE && preferences.scoringOption == ScoringOption.VEGAS && _deckPlays == 1) {
            return true;
        }
        
        if(preferences.drawOption == DrawOption.THREE && preferences.scoringOption == ScoringOption.VEGAS && _deckPlays == 3) {
            return true;
        }
        
        return false;
    }
    
    /**
     * @return The current state of the Talon based on the last operation played
     */
    public TalonCardState getState() {
        return _lastCardHandState;
    }
    
    /**
     * Reverts the last hand played
     */
    public void revertLastHand() {
    	
    	if(JLayeredPane.getLayer(_blankCard) == layeredPane.highestLayer()) {
            _isDeckInRecycledState = false;
            layeredPane.setLayer(_blankCard, layeredPane.lowestLayer() - 1);
        }
        else {
        	
        	if(JLayeredPane.getLayer(_blankCard) == layeredPane.lowestLayer()) {
        		_deckPlays = Math.max(0, --_deckPlays);
        	}
        	
            // Disable all the cards
	        Arrays.asList(layeredPane.getComponents()).forEach(z -> z.setEnabled(false));

	        OptionsPreferences preferences = new OptionsPreferences();
	        preferences.load();
	        
	        if(preferences.drawOption == DrawOption.ONE) {
	            // Get the top-most component and set it underneath the blank card.
	            Component comp = layeredPane.getComponentsInLayer(layeredPane.highestLayer())[0];
	            comp.setVisible(false);
	
	            layeredPane.setLayer(comp, JLayeredPane.getLayer(_blankCard));
	            layeredPane.getComponentsInLayer(layeredPane.highestLayer())[0].setEnabled(true);
	            this.setBounds(comp);
	
	            // From the bottom upwards, re-order the layer of each card
	            Component[] components = layeredPane.getComponents();
	            for(int layer = 0, i = components.length - 1; i >= 0; --i, ++layer) {
	                layeredPane.setLayer(components[i], layer);
	            }
	        }
	        else {
	            // From the blank card upwards, up each components layer by 1
	            int blankCardLayer = JLayeredPane.getLayer(_blankCard);
	            Component[] components = layeredPane.getComponents();
	            for(Component comp : components) {
	                if(layeredPane.getLayer(comp) >= blankCardLayer) {
	                    layeredPane.setLayer(comp, layeredPane.getLayer(comp) + 1);
	                }
	            }
	            
	            // Take the top three cards and move them to the original blank card index
	            //
	            //  Note: Because of how draw three works, we need to do this after all the cards of the layer have had
	            // their visibility properly set to false
	            Component[] cardsBeingReverted = layeredPane.getComponentsInLayer(layeredPane.highestLayer());
	            for(Component comp : cardsBeingReverted) {
	                layeredPane.setLayer(comp, blankCardLayer);
	                comp.setVisible(false);
	            }
	            Arrays.asList(cardsBeingReverted).stream().filter(z -> z instanceof CardView).forEach(z -> this.setBounds(z));
	            
	            // Take the highest layered cards at this point, set their screen position accordingly, and enable
	            // the first card to be moved
	            Component[] highestCards = layeredPane.getComponentsInLayer(layeredPane.highestLayer());
	            
	            // If the card is not the blank card, then remove the cards and re-add them, they will
	            // go through the process of being properly re-positioned
	            if(!(highestCards.length == 1 && highestCards[0] == _blankCard)) {
	               for(Component comp : highestCards) {
	                   setBounds(comp);
	               }
	               
	               highestCards[0].setEnabled(true);
	            }
	        }
        }
    }
    
    /**
     * Displays the next card hand on this view
     */
    public void cycleNextHand() {
        
        // If the talon can no longer be played with, then go no futher
        if(isTalonEnded()) {
            _lastCardHandState = TalonCardState.DECK_PLAYED;
            return;
        }
        
        // If there is only one component then dont go further. The idea is that the "blank" placeholder view that
        // mimics that switching of cards should never be removed from this view, thus if that is the only view that
        // exists then it should mean that all the playing cards having been removed from this view
        if(layeredPane.getComponentCount() == 1) {
            Tracelog.log(Level.INFO, true, "There are no more cards left in the Talon to play.");
            _lastCardHandState = TalonCardState.EMPTY;
            return;
        }
        
        // Notify the movement controller that there was a movement that occured of the talon, from the stock view
        AbstractFactory.getFactory(ControllerFactory.class).get(MovementRecorderController.class).recordMovement(AbstractFactory.getFactory(ViewFactory.class).get(StockView.class), this);
        
        // If we are in a recycle deck state then recycle the deck
        if(_isDeckInRecycledState) {
        	layeredPane.setLayer(_blankCard, layeredPane.lowestLayer() - 1);
            recycleDeck();
        }
        
        // If we are at the end then restart the deck
        if(JLayeredPane.getLayer(_blankCard) == layeredPane.lowestLayer()) {
            // New deck has the score updated
            AbstractFactory.getFactory(ViewFactory.class).get(ScoreView.class).updateScoreDeckFinished(_deckPlays);
            
            // The next pass will recycle the deck
            _isDeckInRecycledState = true;
            
            // Mask the top layer of the deck to make it appear that the deck has reshuffled, this is to make undoing at this
            // stage a million times easier, just flip the card back to the bottom
            layeredPane.setLayer(_blankCard, layeredPane.highestLayer() + 1);
        }
        else {
            
            OptionsPreferences preferences = new OptionsPreferences();
            preferences.load();

            // Disable all the cards
            Arrays.asList(layeredPane.getComponents()).forEach(z -> z.setEnabled(false));
            
            if(preferences.drawOption == DrawOption.ONE) {
                // Get the card that is directly below the blank card
                Component cardDirectlyBelowBlankCard = layeredPane.getComponent(layeredPane.getIndexOf(_blankCard) + 1);
                cardDirectlyBelowBlankCard.setVisible(true);
                
                // Set the layer of the card that is directly below the blank card, to the highest layer
                layeredPane.setLayer(cardDirectlyBelowBlankCard, layeredPane.highestLayer() + 1);
                cardDirectlyBelowBlankCard.setEnabled(true);
                
                this.setBounds(cardDirectlyBelowBlankCard);
                resyncDeckLayers();
            }
            else {
                // Reposition all the cards to the origin
                repositionCardsAboveBlankCardToOrigin();
                
                // Take the blank card and position to the layer one below. Take that layer where the blank card is at
                // and make the other cards top most. Also make sure to position them correctly and set their bounds
                int blankCardLayerIndex = JLayeredPane.getLayer(_blankCard);
                int cardsUnderBlankCard = blankCardLayerIndex - 1;
                int highestLayer = layeredPane.highestLayer();
                Component[] components = layeredPane.getComponentsInLayer(cardsUnderBlankCard);
                for(Component component : components) {
                    component.setVisible(true);
                    component.setEnabled(components[0].equals(component));
                    layeredPane.setLayer(component, highestLayer + 1);
                    this.setBounds(component);
                }
                // Note: Set the layer of the blank card after the positioning of the other cards occur, or the blank card
                // will also be moved back to top, not something we want to do.
                layeredPane.setLayer(_blankCard, cardsUnderBlankCard);
                resyncDeckLayers();
            }

            if(layeredPane.lowestLayer() == JLayeredPane.getLayer(_blankCard)) {
                ++_deckPlays;
                _lastCardHandState = TalonCardState.DECK_PLAYED;
                return;
            }
        }
                
        _lastCardHandState = TalonCardState.NORMAL;
    }
    
    /**
     * @return The position of where the deck of this talon is CURRENTLY is at, which is
     *         based on the blank card location within all the components in the layered pane
     */
    private int getBlankCardPosition() {
        return layeredPane.getIndexOf(_blankCard);
    }
    
    /**
     * Gets the deck position of the specified component, w.r.t the business logic ordering of the deck
     *
     * @param component The component
     * 
     * @return The deck position within the deck
     */
    private int getPosition(Component component) {
        
        int position = 0;
        
        List<Component> components = Arrays.asList(layeredPane.getComponents());
        Collections.reverse(components);

        if(!component.isVisible()) {
            // Take the total number of cards in the layered pane, subtract 1 to remove the special hidden
            // card, and subtract the index of the component.
            position = layeredPane.getComponentCount() - components.indexOf(component) - 1;
        }
        else {
            OptionsPreferences preferences = new OptionsPreferences();
            preferences.load();
            if(preferences.drawOption == DrawOption.ONE) {
                position = components.stream().filter(z -> z.isVisible() && z instanceof CardView).collect(Collectors.toList()).indexOf(component) + 1;
            }
            else {
                
                // Get the list of layers, and make sure they are totally ordered
                SortedSet<Integer> uniqueLayers = new TreeSet<Integer>();
                components.stream().filter(z -> z.isVisible() && z instanceof CardView).forEach(z -> uniqueLayers.add(layeredPane.getLayer(z)));

                List<Component> componentsOrdered = new ArrayList<Component>();
                for(int uniqueLayer : uniqueLayers) {
                    componentsOrdered.addAll(Arrays.asList(layeredPane.getComponentsInLayer(uniqueLayer)));
                }

                position = componentsOrdered.indexOf(component) + 1;
            }
        }
        
        return position;
    }
    
    /**
     * @return The number of cards remaining based on the original count of this view
     */
    private int getCardsRemainingCount() {
        return TOTAL_CARD_SIZE - (layeredPane.getComponentCount() - 1);
    }
     
    /**
     * Recycles the deck
     */
    private void recycleDeck() {
        OptionsPreferences preferences = new OptionsPreferences();
        preferences.load();

        // Remove the blank card from the layered pane, put it back at the end, much easier
        layeredPane.remove(_blankCard);
    	
        // The list of components
        Component[] components = layeredPane.getComponents();
        
        if(preferences.drawOption == DrawOption.ONE) {
            for(int i = components.length - 1; i >= 0; --i) {
                Component component = components[i];
                layeredPane.setLayer(component, i);
                component.setEnabled(false);
                component.setVisible(false);
            }
        }
        else {
            for(int i = 0; i < components.length; ++i) {
            	Component component = components[i];
            	layeredPane.setLayer(component, i / 3);
            	component.setEnabled(false);
                component.setVisible(false);
            }        	
        }
        
        // Add back the blank card as the top-most card
        layeredPane.add(_blankCard);
        layeredPane.setLayer(_blankCard, layeredPane.highestLayer() + 1);

    	// Update the flag indicating that the deck is recycled
    	_isDeckInRecycledState = false;
    }
     
    /*
     * Re-syncs the deck, ensuring that the layers are sequentially ordered
     */
    private void resyncDeckLayers() {
        OptionsPreferences preferences = new OptionsPreferences();
        preferences.load();
        
        if(preferences.drawOption == DrawOption.ONE) {
            // Starting from the lowest layer upwards, re-synchronize all the layer positions of the cards.
            for(int i = layeredPane.getComponentCount() - 1, layerId = 0;  i >= 0; --i, ++layerId) {
                // Re-synchronize the layer position of the card
                layeredPane.setLayer(layeredPane.getComponent(i), layerId);
            }
        }
        else if(preferences.drawOption == DrawOption.THREE) {
            
            // Get the list of components grouped by their layer
            List<Component[]> componentsGroupedByLayer = getComponentsGroupedByLayer();
            
            // Re-input components by their layer, ensuring that their layer is ordered
            // sequentially WITHOUT any gaps between layer numbers
            for(int i = 0; i < componentsGroupedByLayer.size(); ++i) {
                Component[] components = componentsGroupedByLayer.get(i);
                for(int j = 0; j < components.length; ++j) {
                    layeredPane.setLayer(components[j], i);
                }
            }
        }
    }
   
    /**
     * Repositions the list of cards within the layered pane so that the ordering is sequential
     * 
     * (Draw Three Specific)
     */
    private void repositionCardsAboveBlankCardToOrigin() {
        // Before proceeding, everything that has a higher layer than the blank card needs to be re-positioned to the origin
        if(layeredPane.highestLayer() != JLayeredPane.getLayer(_blankCard)) {
            int layer = layeredPane.highestLayer();
            while(layer != JLayeredPane.getLayer(_blankCard)) {
                List<Component> components = Arrays.asList(layeredPane.getComponentsInLayer(layer));
                for(Component component : components) {
                    // For the draw one implementation style
                    this.setBoundsDrawOneImpl(component, getPosition(component));
                }
                
                --layer;
            }
        }
    }
    
    /**
     * Sets the bounds of the specified component
     *
     * @param component The component to set bounds
     */
    private void setBounds(Component component) {

        // Calculate the deck position of the specified component
        int position = getPosition(component);
        
        // The position of the card when playing with `three` is all that concerns us since position matters, vs `single` card which are all stacked.
        OptionsPreferences preferences = new OptionsPreferences();
        preferences.load();
        
        if(preferences.drawOption == DrawOption.THREE) {
            setBoundsDrawThreeImpl(component, position);
        }
        else {
            setBoundsDrawOneImpl(component, position);
        }
    }

    /**
     * Implementation of setting the bounds of the specified component at the specified position
     * when the game is in `Draw Three` mode
     *
     * @param component The component
     * @param position The position
     */
    private void setBoundsDrawOneImpl(Component component, int position) {
        int x = 0;
        int y = 0;
        
        if(position < 12) {
            x = 0;
            y = 0;
        }
        else if (position < 22) {
            x = 2;
            y = 1;
        }
        else {
            x = 4;
            y = 2;
        }
        
        // Set the default bounds of the card view
        Rectangle bounds = new Rectangle(x, y, component.getPreferredSize().width, component.getPreferredSize().height);
        component.setBounds(bounds);
    }
    
    /**
     * Implementation of setting the bounds of the specified component at the specified position
     * when the game is in `Draw Three` mode
     *
     * @param component The component
     * @param position The position
     */
    private void setBoundsDrawThreeImpl(Component component, int position) {
        int xModifier = 0;
        int yModifier = 0;

        if(position < 13) {
            xModifier = 0;
            yModifier = 0;
        }
        else if(position < 22) {
            xModifier = 2;
            yModifier = 1;
            
        }
        else {
            xModifier = 4;
            yModifier = 2;
        }            
        
        // Re-order the cards that exist currently, before determining where this card should be placed.
        Component[] components = layeredPane.getComponentsInLayer(layeredPane.getLayer(component));
        for(int i = components.length - 1; i >= 0; --i) {
            
            Rectangle bounds = new Rectangle(0, 0, component.getPreferredSize().width, component.getPreferredSize().height);
            
            int positionIndex = layeredPane.getPosition(components[i]);
            int offset = 3 - components.length;
            switch(positionIndex + offset) {
            case 0:
                bounds.x = 2 * CARD_OFFSET_X;
                bounds.y = 2;
                break;
            case 1:
                bounds.x = CARD_OFFSET_X;
                bounds.y = 1;
                break;
            case 2:
                bounds.x = 0;
                bounds.y = 0;
                break;
            }
            
            bounds.x += xModifier;
            bounds.y += yModifier;
            
            // Set the new bounds of the specified component
            components[i].setBounds(bounds);
        }            
    }
    
    @Override public void addCard(CardView cardView) {
        OptionsPreferences preferences = new OptionsPreferences();
        preferences.load();
        if(preferences.drawOption == DrawOption.THREE) {
            addCard(cardView, _lastCardInteracted.layer);
            layeredPane.setPosition(cardView, 0);           
        }
        else {
            super.addCard(cardView);
        }
    }
    
    @Override public String toString() {
        StringBuilder builder = new StringBuilder();
        String header = "========" + this.getClass().getSimpleName().toUpperCase() + "========";
        builder.append(header + System.getProperty("line.separator"));
        
        int blankLayer = JLayeredPane.getLayer(_blankCard);
        JLayeredPane blankParentLayeredPane = (JLayeredPane) _blankCard.getParent();
        List<Component> components = Arrays.asList(blankParentLayeredPane.getComponentsInLayer(blankLayer));
        int blankPositionWithinlayer = components.indexOf(_blankCard);
        
        for(Component comp : layeredPane.getComponents()) {
            if(comp instanceof CardView) {
                builder.append(comp + System.getProperty("line.separator"));
            }
            else if(comp.equals(_blankCard)) {
                builder.append("===BLANK CARD===\t[" + blankLayer + "][" + blankPositionWithinlayer + "]" + System.getProperty("line.separator"));
            }
        }
        
        builder.append(System.getProperty("line.separator"));
        builder.append("Decks Played: " + _deckPlays + System.getProperty("line.separator"));
        builder.append("Last Card Hand State: " + _lastCardHandState + System.getProperty("line.separator"));
        builder.append("Undoable Card: " + String.valueOf(_undoableCard) + System.getProperty("line.separator"));
        builder.append("Last Card Interacted: " + String.valueOf(_lastCardInteracted) + System.getProperty("line.separator"));
        builder.append("Is Deck In Recycle State: " + String.valueOf(_isDeckInRecycledState) + System.getProperty("line.separator"));
        builder.append(System.getProperty("line.separator"));
        builder.append(new String(new char[header.length()]).replace("\0", "="));
        
        return builder.toString();
    }
    
    @Override public void render() {
        super.render();
        for(Component comp : layeredPane.getComponents()) {
            if(!comp.equals(_blankCard)) {
                comp.setVisible(false);
            }
        }
    }

    @Override public boolean isValidCollision(Component source) {
        return false;
    }

    @Override public void undoLastAction() {
        
        // If there was a card recoreded
        if(_undoableCard != null ) {
            
            // Get the highest component and set the enabled flag to so that it does not move anymore
            Component highestComponent = layeredPane.getComponentsInLayer(layeredPane.highestLayer())[0];
            highestComponent.setEnabled(false);
            
            // This is required to be done, because when we call `addCard` below, it uses the `_lastCardInteracted`, so we
            // update this value accordingly
            TalonCardReference temp = _lastCardInteracted;
            _lastCardInteracted = _undoableCard;
            addCard(_undoableCard.card);
            _lastCardInteracted = temp;
            
            // Set the bounds of the card
            setBounds(_undoableCard.card);
            repaint();
        }
    }

    @Override public void performBackup() {
        
        // Get the card that is owned by the game view. When a drag occurs, the card is owned by the game view so that
        // it can be freely dragged around the entire game.
        CardView cardView = AbstractFactory.getFactory(ViewFactory.class).get(GameView.class).getCardComponent();
        
        // If the card cannot be found and if the talon doesnt have the blank card as the top most card
        if(cardView == null && layeredPane.highestLayer() != JLayeredPane.getLayer(_blankCard)) {
            
            // Take the card that is at the top-most of the talon. This is the case when we are
            // playing in outline mode, and the card still exists on the talon, because the card proxy
            // is the thing that is actually movings
            cardView = (CardView) layeredPane.getComponentsInLayer(layeredPane.highestLayer())[0];
        }
        
        // If no card was specified and a backup is required, take the top most
        // card. This can only happen if an automove occurs
        if(_lastCardInteracted == null) {
            _lastCardInteracted = new TalonCardReference(getLastCard());
        }
        
        // Set the undoable card as the card that can be undone
        _undoableCard = new TalonCardReference(cardView, _lastCardInteracted.layer);            
    }

    @Override public void clearBackup() {
        _undoableCard = null;
    }

    @Override protected Point getCardOffset(CardView cardView) {
        // Not needed
        return new Point(0, 0);
    }

    @Override public void onCollisionStart(Component source) {
        // Not needed
    }

    @Override public void onCollisionStop(Component source) {
        // Not needed
    }
}