/*
 * Copyright (c) 2015-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opencypher.railroad;

import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

public final class AwtShapes implements ShapeRenderer.Shapes<RuntimeException>
{
    public static final Diagram.CanvasProvider<AwtShapes, RuntimeException> AWT = AwtShapes::new;
    private final String name;
    private final double width;
    private final double height;
    private final List<Element> elements = new ArrayList<>();

    private AwtShapes( String name, double width, double height )
    {
        this.name = name;
        this.width = width;
        this.height = height;
    }

    public String getName()
    {
        return name;
    }

    public double getWidth()
    {
        return width;
    }

    public double getHeight()
    {
        return height;
    }

    public void render( Graphics2D target )
    {
        for ( Element element : elements )
        {
            element.render( target );
        }
    }

    private static abstract class Element
    {
        abstract void render( Graphics2D target );
    }

    private static class ShapeElement extends Element
    {
        private final Style style;
        private final Shape shape;

        ShapeElement( Style style, Shape shape )
        {
            this.style = style;
            this.shape = shape;
        }

        @Override
        void render( Graphics2D target )
        {
            if ( style.stroke )
            {
                target.draw( shape );
            }
            if ( style.fill )
            {
                target.fill( shape );
            }
        }
    }

    private static class TextElement extends Element
    {
        private final double x, y;
        private final TextGlyphs text;

        TextElement( double x, double y, TextGlyphs text )
        {
            this.x = x;
            this.y = y;
            this.text = text;
        }

        @Override
        void render( Graphics2D target )
        {
            target.setFont( text.getFont() );
            target.drawString( text.text(), text.offsetX( x ), text.offsetY( y ) );
        }
    }

    @Override
    public void roundRect( Style style, double x, double y, double width, double height, double diameter )
    {
        elements.add(
                new ShapeElement( style, new RoundRectangle2D.Double( x, y, width, height, diameter, diameter ) ) );
    }

    @Override
    public void rect( Style style, double x, double y, double width, double height )
    {
        elements.add( new ShapeElement( style, new Rectangle2D.Double( x, y, width, height ) ) );
    }

    @Override
    public void arc( Style style, double cx, double cy, double radius, double start, double extent )
    {
        double diameter = radius * 2, x = cx - radius, y = cy - radius;
        if ( extent >= 360 || extent <= -360 )
        {
            elements.add( new ShapeElement( style, new Ellipse2D.Double( x, y, diameter, diameter ) ) );
        }
        else
        {
            elements.add( new ShapeElement(
                    style, new Arc2D.Double( x, y, diameter, diameter, start, extent, Arc2D.OPEN ) ) );
        }
    }

    @Override
    public void line( Style style, double x1, double y1, double x2, double y2 )
    {
        elements.add( new ShapeElement( Style.OUTLINE, new Line2D.Double( x1, y1, x2, y2 ) ) );
    }

    @Override
    public void polygon( Style style, Point... points )
    {
        int[] x = new int[points.length], y = new int[points.length];
        for ( int i = 0; i < points.length; i++ )
        {
            x[i] = (int) Math.round( points[i].x );
            y[i] = (int) Math.round( points[i].y );
        }
        elements.add( new ShapeElement( style, new Polygon( x, y, points.length ) ) );
    }

    @Override
    public void text( TextGlyphs text, double x, double y )
    {
        elements.add( new TextElement( x, y, text ) );
    }

    @Override
    public Path<RuntimeException> path( Style style )
    {
        throw new UnsupportedOperationException( "not implemented" );
    }
}
