import com.nyora.hasan72341.sdk.NyoraSources;
import com.nyora.hasan72341.shared.extension.MangaDetails;
import com.nyora.hasan72341.shared.extension.MangaSearchPage;
import com.nyora.hasan72341.shared.model.Manga;
import com.nyora.hasan72341.shared.model.MangaChapter;
import com.nyora.hasan72341.shared.model.MangaPage;
import com.nyora.hasan72341.shared.model.MangaSource;

import java.util.List;

/** Minimal end-to-end demo of the in-process JVM SDK. Run against a source id, e.g. parser:MANGADEX. */
public class Demo {
    public static void main(String[] args) throws Exception {
        NyoraSources nyora = NyoraSources.create();

        List<MangaSource> catalog = nyora.catalog();
        System.out.println("catalog sources: " + catalog.size());

        String sid = args.length > 0 ? args[0] : "parser:MANGADEX";
        MangaSearchPage popular = nyora.popular(sid, 1);
        System.out.println("popular entries: " + popular.getEntries().size());
        if (popular.getEntries().isEmpty()) return;

        Manga first = popular.getEntries().get(0);
        MangaDetails d = nyora.details(sid, first.getUrl().isEmpty() ? first.getId() : first.getUrl());
        System.out.println(first.getTitle() + " → " + d.getChapters().size() + " chapters");

        if (!d.getChapters().isEmpty()) {
            MangaChapter ch = d.getChapters().get(0);
            List<MangaPage> pages = nyora.pages(sid, ch);
            System.out.println("chapter '" + ch.getTitle() + "' → " + pages.size() + " pages");
        }
        System.out.println("OK — in-process parse worked");
    }
}
