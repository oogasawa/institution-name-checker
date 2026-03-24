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

    @POST
    @Path("/check/{code}")
    public Response check(@PathParam("code") String code) {
        Institution inst = store.findByCode(code);
        if (inst != null) {
            browserService.openForReview(inst.nameJa, inst.url);
        }
        return Response.ok().build();
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
