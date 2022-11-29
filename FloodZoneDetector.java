package ...;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Log4j2
@Service
public class FloodZoneDetector {

    final String getFloodMapLayer = "https://hazards.fema.gov/gis/nfhl/rest/services/FIRMette/NFHLREST_FIRMette/MapServer/export?dpi=96&transparent=true&format=png32&bbox=%s,%s,%s,%s&bboxSR=102100&imageSR=102100&size=968,500&f=image&layers=show:20";

    public FloodZone getFloodZone(String address) throws IOException {
        String findAddressCandidateOp = "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates?SingleLine=" + URLEncoder.encode(address, StandardCharsets.UTF_8.toString()) + "&f=json&outSR=%7B%22wkid%22%3A102100%7D&outFields=*";
        log.info(findAddressCandidateOp);
        URL url = new URL(findAddressCandidateOp);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.63 Safari/537.36");
        conn.setDoOutput(true);

        InputStream in = new BufferedInputStream(conn.getInputStream());
        String text = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

        JsonObject json = new JsonParser().parse(text).getAsJsonObject();

        JsonObject candidate = json.get("candidates").getAsJsonArray().get(0).getAsJsonObject();

        JsonObject location = candidate.get("location").getAsJsonObject();
        double x = location.get("x").getAsDouble();
        double y = location.get("y").getAsDouble();

        JsonObject attributes = candidate.get("extent").getAsJsonObject();
        Double xmin = attributes.get("xmin").getAsDouble();
        Double ymin = attributes.get("ymin").getAsDouble();
        Double xmax = attributes.get("xmax").getAsDouble();
        Double ymax = attributes.get("ymax").getAsDouble();

        int wkid = json.get("spatialReference").getAsJsonObject().get("wkid").getAsInt();

        String getTileOp = String.format(getFloodMapLayer, xmin.toString(), ymin.toString(), xmax.toString(), ymax.toString());
        log.info(getTileOp);

        url = new URL(getTileOp);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.63 Safari/537.36");
        conn.setDoOutput(true);
        in = new BufferedInputStream(conn.getInputStream());
        BufferedImage bf = ImageIO.read(in);

        double pixelXRatio = (xmax - x) / (xmax - xmin);
        double pixelYRatio = (ymax - y) / (ymax - ymin);
        int pixelX = (int) Math.round(bf.getWidth() * pixelXRatio);
        int pixelY = (int) Math.round(bf.getHeight() * pixelYRatio);
        int rbg = bf.getRGB(pixelX, pixelY);
        Color color = new Color(rbg, true);
        log.info(color.toString() + "alpha=" + color.getAlpha());

        //TODO : improve the correctness of detecting zone : there are more than 2 zones, there are some texts on the layer
        if (!isEmpty(color)) { // not transparent point
            if (isLikeBlue(color)) { // like a Blue
                return FloodZone.A;
            } else if (isOrangeMixBlack(bf, pixelX, pixelY)) {
                return FloodZone.X;
            } else if (isLikeOrange(color)) {
                return FloodZone.V;
            } else if (isLikeYellow(color)) {
                return FloodZone.D;
            }
        }
        return null;
    }
    private boolean isLikeBlue(Color color) {
        return color.getBlue() > 200;
    }

    private boolean isLikeOrange(Color color) {
        return color.getRed() > 200 && color.getBlue() < 10;
    }

    private boolean isLikeYellow(Color color) {
        return color.getRed() > 200 && color.getBlue() > 100;
    }

    private boolean isWhite(Color color) {
        return color.getRed() > 240 && color.getGreen() > 240 && color.getBlue() > 240;
    }

    private boolean isBlack(Color color) {
        return color.getBlue() == 0 && color.getGreen() == 0 && color.getRed() == 0;
    }

    private boolean isOrangeMixBlack(BufferedImage image, int X, int Y) {
        final int distance = 15;
        int rbg = image.getRGB(X, Y);
        int rbg2;
        if (X < (image.getWidth() - distance)) {
            int X1 = X + distance;
            rbg2 = image.getRGB(X1, Y);
        } else {
            int X1 = X - distance;
            rbg2 = image.getRGB(X1, Y);
        }
        Color color1 = new Color(rbg, true);
        Color color2 = new Color(rbg2, true);
        if (isBlack(color1) && isLikeOrange(color2) || isLikeOrange(color1) && isBlack(color2)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isEmpty(Color color) {
        return color.getAlpha() < 10;
    }

    // zone D: java.awt.Color[r=242,g=229,b=116] (yellow)
    // zone V : java.awt.Color[r=255,g=129,b=0] (orange)
    // zone X : java.awt.Color[r=0,g=0,b=0] java.awt.Color[r=255,g=129,b=0] (black + orange)
}
