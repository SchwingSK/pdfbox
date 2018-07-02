/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.pdmodel.graphics.image;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Random;
import javax.imageio.ImageIO;
import junit.framework.TestCase;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceCMYK;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import static org.apache.pdfbox.pdmodel.graphics.image.ValidateXImage.checkIdent;
import static org.apache.pdfbox.pdmodel.graphics.image.ValidateXImage.colorCount;
import static org.apache.pdfbox.pdmodel.graphics.image.ValidateXImage.doWritePDF;
import static org.apache.pdfbox.pdmodel.graphics.image.ValidateXImage.validate;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Unit tests for LosslessFactory
 *
 * @author Tilman Hausherr
 */
public class LosslessFactoryTest extends TestCase
{
    private final File testResultsDir = new File("target/test-output/graphics");

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        testResultsDir.mkdirs();
    }

    /**
     * Tests RGB LosslessFactoryTest#createFromImage(PDDocument document,
     * BufferedImage image)
     *
     * @throws java.io.IOException
     */
    public void testCreateLosslessFromImageRGB() throws IOException
    {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(this.getClass().getResourceAsStream("png.png"));

        PDImageXObject ximage1 = LosslessFactory.createFromImage(document, image);
        validate(ximage1, 8, image.getWidth(), image.getHeight(), "png", PDDeviceRGB.INSTANCE.getName());
        checkIdent(image, ximage1.getImage());

        // Create a grayscale image
        BufferedImage grayImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = grayImage.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        PDImageXObject ximage2 = LosslessFactory.createFromImage(document, grayImage);
        validate(ximage2, 8, grayImage.getWidth(), grayImage.getHeight(), "png", PDDeviceGray.INSTANCE.getName());
        checkIdent(grayImage, ximage2.getImage());

        // Create a bitonal image
        BufferedImage bitonalImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);

        // avoid multiple of 8 to test padding
        assertFalse(bitonalImage.getWidth() % 8 == 0);
        
        g = bitonalImage.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        PDImageXObject ximage3 = LosslessFactory.createFromImage(document, bitonalImage);
        validate(ximage3, 1, bitonalImage.getWidth(), bitonalImage.getHeight(), "png", PDDeviceGray.INSTANCE.getName());
        checkIdent(bitonalImage, ximage3.getImage());

        // This part isn't really needed because this test doesn't break
        // if the mask has the wrong colorspace (PDFBOX-2057), but it is still useful
        // if something goes wrong in the future and we want to have a PDF to open.
        PDPage page = new PDPage();
        document.addPage(page);
        PDPageContentStream contentStream = new PDPageContentStream(document, page, AppendMode.APPEND, false);
        contentStream.drawImage(ximage1, 200, 300, ximage1.getWidth() / 2, ximage1.getHeight() / 2);
        contentStream.drawImage(ximage2, 200, 450, ximage2.getWidth() / 2, ximage2.getHeight() / 2);
        contentStream.drawImage(ximage3, 200, 600, ximage3.getWidth() / 2, ximage3.getHeight() / 2);
        contentStream.close();
        
        File pdfFile = new File(testResultsDir, "misc.pdf");
        document.save(pdfFile);
        document.close();
        
        document = PDDocument.load(pdfFile, (String)null);
        new PDFRenderer(document).renderImage(0);
        document.close();
    }

    /**
     * Tests INT_ARGB LosslessFactoryTest#createFromImage(PDDocument document,
     * BufferedImage image)
     *
     * @throws java.io.IOException
     */
    public void testCreateLosslessFromImageINT_ARGB() throws IOException
    {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(this.getClass().getResourceAsStream("png.png"));

        // create an ARGB image
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage argbImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics ag = argbImage.getGraphics();
        ag.drawImage(image, 0, 0, null);
        ag.dispose();

        for (int x = 0; x < argbImage.getWidth(); ++x)
        {
            for (int y = 0; y < argbImage.getHeight(); ++y)
            {
                argbImage.setRGB(x, y, (argbImage.getRGB(x, y) & 0xFFFFFF) | ((y / 10 * 10) << 24));
            }
        }

        PDImageXObject ximage = LosslessFactory.createFromImage(document, argbImage);
        validate(ximage, 8, argbImage.getWidth(), argbImage.getHeight(), "png", PDDeviceRGB.INSTANCE.getName());
        checkIdent(argbImage, ximage.getImage());
        checkIdentRGB(argbImage, ximage.getOpaqueImage());

        assertNotNull(ximage.getSoftMask());
        validate(ximage.getSoftMask(), 8, argbImage.getWidth(), argbImage.getHeight(), "png", PDDeviceGray.INSTANCE.getName());
        assertTrue(colorCount(ximage.getSoftMask().getImage()) > image.getHeight() / 10);

        doWritePDF(document, ximage, testResultsDir, "intargb.pdf");
    }

    /**
     * Tests INT_ARGB LosslessFactoryTest#createFromImage(PDDocument document,
     * BufferedImage image) with BITMASK transparency
     *
     * @throws java.io.IOException
     */
    public void testCreateLosslessFromImageBITMASK_INT_ARGB() throws IOException
    {
        doBitmaskTransparencyTest(BufferedImage.TYPE_INT_ARGB, "bitmaskintargb.pdf");
    }

    /**
     * Tests 4BYTE_ABGR LosslessFactoryTest#createFromImage(PDDocument document,
     * BufferedImage image) with BITMASK transparency
     *
     * @throws java.io.IOException
     */
    public void testCreateLosslessFromImageBITMASK4BYTE_ABGR() throws IOException
    {
        doBitmaskTransparencyTest(BufferedImage.TYPE_4BYTE_ABGR, "bitmask4babgr.pdf");
    }

    /**
     * Tests 4BYTE_ABGR LosslessFactoryTest#createFromImage(PDDocument document,
     * BufferedImage image)
     *
     * @throws java.io.IOException
     */
    public void testCreateLosslessFromImage4BYTE_ABGR() throws IOException
    {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(this.getClass().getResourceAsStream("png.png"));

        // create an ARGB image
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage argbImage = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics ag = argbImage.getGraphics();
        ag.drawImage(image, 0, 0, null);
        ag.dispose();

        for (int x = 0; x < argbImage.getWidth(); ++x)
        {
            for (int y = 0; y < argbImage.getHeight(); ++y)
            {
                argbImage.setRGB(x, y, (argbImage.getRGB(x, y) & 0xFFFFFF) | ((y / 10 * 10) << 24));
            }
        }

        // extra for PDFBOX-3181: check for exception due to different sizes of 
        // alphaRaster.getSampleModel().getWidth()
        // and
        // alphaRaster.getWidth()
        // happens with image returned by BufferedImage.getSubimage()
        argbImage = argbImage.getSubimage(1, 1, argbImage.getWidth() - 2, argbImage.getHeight() - 2);
        w -= 2;
        h -= 2;

        PDImageXObject ximage = LosslessFactory.createFromImage(document, argbImage);

        validate(ximage, 8, w, h, "png", PDDeviceRGB.INSTANCE.getName());
        checkIdent(argbImage, ximage.getImage());
        checkIdentRGB(argbImage, ximage.getOpaqueImage());

        assertNotNull(ximage.getSoftMask());
        validate(ximage.getSoftMask(), 8, w, h, "png", PDDeviceGray.INSTANCE.getName());
        assertTrue(colorCount(ximage.getSoftMask().getImage()) > image.getHeight() / 10);

        doWritePDF(document, ximage, testResultsDir, "4babgr.pdf");
    }

    /**
     * Tests LosslessFactoryTest#createFromImage(PDDocument document,
     * BufferedImage image) with transparent GIF
     *
     * @throws java.io.IOException
     */
    public void testCreateLosslessFromTransparentGIF() throws IOException
    {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(this.getClass().getResourceAsStream("gif.gif"));
        
        assertEquals(Transparency.BITMASK, image.getColorModel().getTransparency());

        PDImageXObject ximage = LosslessFactory.createFromImage(document, image);

        int w = image.getWidth();
        int h = image.getHeight();
        validate(ximage, 8, w, h, "png", PDDeviceRGB.INSTANCE.getName());
        checkIdent(image, ximage.getImage());
        checkIdentRGB(image, ximage.getOpaqueImage());

        assertNotNull(ximage.getSoftMask());
        validate(ximage.getSoftMask(), 1, w, h, "png", PDDeviceGray.INSTANCE.getName());
        assertEquals(2, colorCount(ximage.getSoftMask().getImage()));

        doWritePDF(document, ximage, testResultsDir, "gif.pdf");
    }

    /**
     * Test file that had a predictor encoding bug in PDFBOX-4184.
     *
     * @throws java.io.IOException
     */
    public void testCreateLosslessFromGovdocs032163() throws IOException
    {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(new File("target/imgs", "PDFBOX-4184-032163.jpg"));
        PDImageXObject ximage = LosslessFactory.createFromImage(document, image);
        validate(ximage, 8, image.getWidth(), image.getHeight(), "png", PDDeviceRGB.INSTANCE.getName());
        checkIdent(image, ximage.getImage());
    }

    /**
     * Check whether the RGB part of images are identical.
     *
     * @param expectedImage
     * @param actualImage
     */
    private void checkIdentRGB(BufferedImage expectedImage, BufferedImage actualImage)
    {
        String errMsg = "";

        int w = expectedImage.getWidth();
        int h = expectedImage.getHeight();
        assertEquals(w, actualImage.getWidth());
        assertEquals(h, actualImage.getHeight());
        for (int y = 0; y < h; ++y)
        {
            for (int x = 0; x < w; ++x)
            {
                if ((expectedImage.getRGB(x, y) & 0xFFFFFF) != (actualImage.getRGB(x, y) & 0xFFFFFF))
                {
                    errMsg = String.format("(%d,%d) %06X != %06X", x, y, expectedImage.getRGB(x, y) & 0xFFFFFF, actualImage.getRGB(x, y) & 0xFFFFFF);
                }
                assertEquals(errMsg, expectedImage.getRGB(x, y) & 0xFFFFFF, actualImage.getRGB(x, y) & 0xFFFFFF);
            }
        }
    }

    private void doBitmaskTransparencyTest(int imageType, String pdfFilename) throws IOException
    {
        PDDocument document = new PDDocument();

        int width = 257;
        int height = 256;

        // create an ARGB image
        BufferedImage argbImage = new BufferedImage(width, height, imageType);

        // from there, create an image with Transparency.BITMASK
        Graphics2D g = argbImage.createGraphics();
        GraphicsConfiguration gc = g.getDeviceConfiguration();
        argbImage = gc.createCompatibleImage(width, height, Transparency.BITMASK);
        g.dispose();
        // create a red rectangle
        g = argbImage.createGraphics();
        g.setColor(Color.red);
        g.fillRect(0, 0, width, height);
        g.dispose();

        Random random = new Random();
        random.setSeed(12345);
        // create a transparency cross: only pixels in the 
        // interval max/2 - max/8 ... max/2 + max/8 will be visible
        int startX = width / 2 - width / 8;
        int endX = width / 2 + width / 8;
        int startY = height / 2 - height / 8;
        int endY = height / 2 + height / 8;
        for (int x = 0; x < width; ++x)
        {
            for (int y = 0; y < height; ++y)
            {
                // create pseudorandom alpha values, but those within the cross
                // must be >= 128 and those outside must be < 128
                int alpha;
                if ((x >= startX && x <= endX) || y >= startY && y <= endY)
                {
                    alpha = 128 + (int) (random.nextFloat() * 127);
                    assertTrue(alpha >= 128);
                    argbImage.setRGB(x, y, (argbImage.getRGB(x, y) & 0xFFFFFF) | (alpha << 24));
                    assertEquals(255, argbImage.getRGB(x, y) >>> 24);
                }
                else
                {
                    alpha = (int) (random.nextFloat() * 127);
                    assertTrue(alpha < 128);
                    argbImage.setRGB(x, y, (argbImage.getRGB(x, y) & 0xFFFFFF) | (alpha << 24));
                    assertEquals(0, argbImage.getRGB(x, y) >>> 24);
                }
            }
        }

        PDImageXObject ximage = LosslessFactory.createFromImage(document, argbImage);
        validate(ximage, 8, width, height, "png", PDDeviceRGB.INSTANCE.getName());
        checkIdent(argbImage, ximage.getImage());
        checkIdentRGB(argbImage, ximage.getOpaqueImage());

        assertNotNull(ximage.getSoftMask());
        validate(ximage.getSoftMask(), 1, width, height, "png", PDDeviceGray.INSTANCE.getName());
        assertEquals(2, colorCount(ximage.getSoftMask().getImage()));

        // check whether the mask is a b/w cross
        BufferedImage maskImage = ximage.getSoftMask().getImage();
        
        // avoid multiple of 8 to test padding
        assertFalse(maskImage.getWidth() % 8 == 0);
        
        assertEquals(Transparency.OPAQUE, maskImage.getTransparency());
        for (int x = 0; x < width; ++x)
        {
            for (int y = 0; y < height; ++y)
            {
                if ((x >= startX && x <= endX) || y >= startY && y <= endY)
                {
                    assertEquals(0xFFFFFF, maskImage.getRGB(x, y) & 0xFFFFFF);
                }
                else
                {
                    assertEquals(0, maskImage.getRGB(x, y) & 0xFFFFFF);
                }
            }
        }

        // This part isn't really needed because this test doesn't break
        // if the mask has the wrong colorspace (PDFBOX-2057), but it is still useful
        // if something goes wrong in the future and we want to have a PDF to open.
        // Create a rectangle
        BufferedImage rectImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        g = rectImage.createGraphics();
        g.setColor(Color.blue);
        g.fillRect(0, 0, width, height);
        g.dispose();
        PDImageXObject ximage2 = LosslessFactory.createFromImage(document, rectImage);

        PDPage page = new PDPage();
        document.addPage(page);
        PDPageContentStream contentStream = new PDPageContentStream(document, page, AppendMode.APPEND, false);
        contentStream.drawImage(ximage2, 150, 300, ximage2.getWidth(), ximage2.getHeight());
        contentStream.drawImage(ximage, 150, 300, ximage.getWidth(), ximage.getHeight());
        contentStream.close();
        File pdfFile = new File(testResultsDir, pdfFilename);
        document.save(pdfFile);
        document.close();
        document = PDDocument.load(pdfFile, (String)null);
        new PDFRenderer(document).renderImage(0);
        document.close();
    }

    /**
     * Test lossless encoding of CMYK images
     */
    public void testCreateLosslessFromImageCMYK() throws IOException
    {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(this.getClass().getResourceAsStream("png.png"));

        final ColorSpace targetCS = new ICC_ColorSpace(ICC_Profile
                .getInstance(this.getClass().getResourceAsStream("/org/apache/pdfbox/resources/icc/ISOcoated_v2_300_bas.icc")));
        ColorConvertOp op = new ColorConvertOp(image.getColorModel().getColorSpace(), targetCS, null);
        BufferedImage imageCMYK = op.filter(image, null);

        PDImageXObject ximage = LosslessFactory.createFromImage(document, imageCMYK);
        validate(ximage, 8, imageCMYK.getWidth(), imageCMYK.getHeight(), "png", PDDeviceCMYK.INSTANCE.getName());

        doWritePDF(document, ximage, testResultsDir, "cmyk.pdf");

        // The image in CMYK got color-truncated because the ISO_Coated colorspace is way smaller 
        // than the sRGB colorspace. The image is converted back to sRGB when calling PDImageXObject.getImage().
        // So to be able to check the image data we must also convert our CMYK Image back to sRGB
        //BufferedImage compareImageRGB = new BufferedImage(imageCMYK.getWidth(), imageCMYK.getHeight(),
        //BufferedImage.TYPE_INT_BGR);
        //Graphics2D graphics = compareImageRGB.createGraphics();
        //graphics.drawImage(imageCMYK, 0, 0, null);
        //graphics.dispose();
        //ImageIO.write(compareImageRGB, "TIFF", new File("/tmp/compare.tiff"));
        //ImageIO.write(ximage.getImage(), "TIFF", new File("/tmp/compare2.tiff"));
        //checkIdent(compareImageRGB, ximage.getImage());
    }

    public void testCreateLosslessFrom16Bit() throws IOException
    {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(this.getClass().getResourceAsStream("png.png"));

        ColorSpace targetCS = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int dataBufferType = DataBuffer.TYPE_USHORT;
        final ColorModel colorModel = new ComponentColorModel(targetCS, false, false,
                ColorModel.OPAQUE, dataBufferType);
        WritableRaster targetRaster = Raster.createInterleavedRaster(dataBufferType, image.getWidth(), image.getHeight(),
                targetCS.getNumComponents(), new Point(0, 0));
        BufferedImage img16Bit = new BufferedImage(colorModel, targetRaster, false, new Hashtable());
        ColorConvertOp op = new ColorConvertOp(image.getColorModel().getColorSpace(), targetCS, null);
        op.filter(image, img16Bit);

        PDImageXObject ximage = LosslessFactory.createFromImage(document, img16Bit);
        validate(ximage, 16, img16Bit.getWidth(), img16Bit.getHeight(), "png", PDDeviceRGB.INSTANCE.getName());
        checkIdent(image, ximage.getImage());
        doWritePDF(document, ximage, testResultsDir, "misc-16bit.pdf");
    }

    public void testCreateLosslessFromImageINT_BGR() throws IOException
    {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(this.getClass().getResourceAsStream("png.png"));

        BufferedImage imgBgr = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_BGR);
        Graphics2D graphics = imgBgr.createGraphics();
        graphics.drawImage(image, 0, 0, null);

        PDImageXObject ximage = LosslessFactory.createFromImage(document, imgBgr);
        validate(ximage, 8, imgBgr.getWidth(), imgBgr.getHeight(), "png", PDDeviceRGB.INSTANCE.getName());
        checkIdent(image, ximage.getImage());
    }

    public void testCreateLosslessFromImageINT_RGB() throws IOException
    {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(this.getClass().getResourceAsStream("png.png"));

        BufferedImage imgRgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = imgRgb.createGraphics();
        graphics.drawImage(image, 0, 0, null);

        PDImageXObject ximage = LosslessFactory.createFromImage(document, imgRgb);
        validate(ximage, 8, imgRgb.getWidth(), imgRgb.getHeight(), "png", PDDeviceRGB.INSTANCE.getName());
        checkIdent(image, ximage.getImage());
    }

    public void testCreateLosslessFromImageBYTE_3BGR() throws IOException
    {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(this.getClass().getResourceAsStream("png.png"));

        BufferedImage imgRgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = imgRgb.createGraphics();
        graphics.drawImage(image, 0, 0, null);

        PDImageXObject ximage = LosslessFactory.createFromImage(document, imgRgb);
        validate(ximage, 8, imgRgb.getWidth(), imgRgb.getHeight(), "png", PDDeviceRGB.INSTANCE.getName());
        checkIdent(image, ximage.getImage());
    }

    public void testCreateLosslessFrom16BitPNG() throws IOException
    {
        PDDocument document = new PDDocument();
        BufferedImage image = ImageIO.read(new File("target/imgs", "PDFBOX-4184-16bit.png"));

        assertEquals(64, image.getColorModel().getPixelSize());
        assertEquals(Transparency.TRANSLUCENT, image.getColorModel().getTransparency());
        assertEquals(4, image.getRaster().getNumDataElements());
        assertEquals(java.awt.image.DataBuffer.TYPE_USHORT, image.getRaster().getDataBuffer().getDataType());

        PDImageXObject ximage = LosslessFactory.createFromImage(document, image);

        int w = image.getWidth();
        int h = image.getHeight();
        validate(ximage, 16, w, h, "png", PDDeviceRGB.INSTANCE.getName());
        checkIdent(image, ximage.getImage());
        checkIdentRGB(image, ximage.getOpaqueImage());

        assertNotNull(ximage.getSoftMask());
        validate(ximage.getSoftMask(), 16, w, h, "png", PDDeviceGray.INSTANCE.getName());
        assertEquals(35, colorCount(ximage.getSoftMask().getImage()));

        doWritePDF(document, ximage, testResultsDir, "png16bit.pdf");
    }
}
