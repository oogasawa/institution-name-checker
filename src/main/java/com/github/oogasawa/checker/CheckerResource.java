package com.github.oogasawa.checker;

import java.net.URI;
import java.util.List;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
public class CheckerResource {

    @Inject
    Template index;

    @Inject
    InstitutionStore store;

    @Inject
    BrowserService browserService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list(
            @QueryParam("filter") @DefaultValue("all") String filter,
            @QueryParam("range") @DefaultValue("-1") int range) {

        List<Integer> ranges = store.getAvailableRanges();
        if (range < 0 && !ranges.isEmpty()) {
            range = ranges.getFirst();
        }

        var all = store.getByRange(range);
        var filtered = switch (filter) {
            case "missing" -> all.stream().filter(i -> i.nameEn.isEmpty()).toList();
            case "has_en" -> all.stream().filter(i -> !i.nameEn.isEmpty()).toList();
            default -> all;
        };

        long missingCount = store.countMissing();
        String rangeLabel = String.format("%d0000-%d9999", range / 10000, range / 10000);

        return index.data("institutions", filtered)
                .data("filter", filter)
                .data("range", range)
                .data("ranges", ranges)
                .data("rangeLabel", rangeLabel)
                .data("totalCount", filtered.size())
                .data("missingCount", missingCount);
    }

    @GET
    @Path("/launch/{code}")
    @Produces(MediaType.TEXT_HTML)
    public Response launch(@PathParam("code") String code) {
        Institution inst = store.findByCode(code);
        if (inst == null) {
            return Response.status(404).entity("Not found: " + code).build();
        }
        String encodedName = java.net.URLEncoder.encode(inst.nameJa, java.nio.charset.StandardCharsets.UTF_8);
        String encodedQuery = java.net.URLEncoder.encode(inst.nameJa + " English name", java.nio.charset.StandardCharsets.UTF_8);
        String url1 = "https://duckduckgo.com/?q=" + encodedName;
        String url2 = (inst.url != null && !inst.url.isEmpty()) ? inst.url : null;
        String url3 = "https://duckduckgo.com/?q=" + encodedQuery;

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Launching tabs for ").append(code).append("</title></head><body>");
        html.append("<p>Opening tabs for ").append(inst.nameJa).append("...</p>");
        html.append("<script>\n");
        // Open first URL in a new window, then add remaining URLs as tabs in that window
        html.append("var w = window.open('").append(url1).append("', '_blank');\n");
        html.append("setTimeout(function(){\n");
        if (url2 != null) {
            html.append("  window.open('").append(url2.replace("'", "\\'")).append("', '_blank');\n");
        }
        html.append("  window.open('").append(url3).append("', '_blank');\n");
        html.append("  setTimeout(function(){ window.close(); }, 500);\n");
        html.append("}, 300);\n");
        html.append("</script></body></html>");

        return Response.ok(html.toString(), MediaType.TEXT_HTML).build();
    }

    @POST
    @Path("/update/{code}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response update(@PathParam("code") String code,
                           @FormParam("nameEn") String nameEn) {
        store.updateNameEn(code, nameEn != null ? nameEn.trim() : "");
        store.save();
        return Response.ok().build();
    }

    @POST
    @Path("/reload")
    public Response reload() {
        store.load();
        return Response.seeOther(URI.create("/")).build();
    }
}
