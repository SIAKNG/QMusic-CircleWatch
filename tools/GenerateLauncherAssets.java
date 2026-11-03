import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Packages approved artwork at appropriate Android resource resolutions.
 * This performs only density export; composition and foreground matting are
 * authored with image_gen. No artwork is cropped or repositioned here.
 */
class GenerateLauncherAssets {
    static void export(Path source, Path target, int size) throws Exception {
        BufferedImage input = ImageIO.read(source.toFile());
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = output.createGraphics();
        canvas.setComposite(AlphaComposite.Src);
        canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        canvas.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        canvas.drawImage(input, 0, 0, size, size, null);
        canvas.dispose();
        Files.createDirectories(target.getParent());
        if (!ImageIO.write(output, "PNG", target.toFile())) throw new IllegalStateException("PNG exporter unavailable");
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath();
        Path master = root.resolve("branding/source/approved-dark-ring.png");
        Path mark = root.resolve("branding/source/adaptive-mark.png");
        Path drawable = root.resolve("app/src/main/res/drawable-nodpi");
        export(master, drawable.resolve("ic_launcher.png"), 192);
        export(master, drawable.resolve("qmusic_circlewatch_icon.png"), 512);
        export(mark, drawable.resolve("qmusic_circlewatch_mark.png"), 512);
        Path brand = root.resolve("branding/qmusic-circlewatch-icon.png");
        export(master, brand, 1080);
        Files.copy(brand, root.resolve("branding/qmusic-watch-icon.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String data = Base64.getEncoder().encodeToString(Files.readAllBytes(brand));
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1080\" height=\"1080\" viewBox=\"0 0 1080 1080\" role=\"img\" aria-label=\"QMusic-CircleWatch icon\"><image width=\"1080\" height=\"1080\" href=\"data:image/png;base64," + data + "\"/></svg>\n";
        Files.writeString(root.resolve("branding/qmusic-circlewatch-icon.svg"), svg, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("branding/qmusic-watch-icon.svg"), svg, StandardCharsets.UTF_8);
        System.out.println("Exported approved icon densities without cropping the ring or crown.");
    }
}
