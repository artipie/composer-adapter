/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 artipie.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.artipie.composer;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.composer.misc.ContentAsJson;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * PHP Composer packages registry built from JSON.
 *
 * @since 0.1
 */
public final class JsonPackages implements Packages {

    /**
     * Root attribute value for packages registry in JSON.
     */
    private static final String ATTRIBUTE = "packages";

    /**
     * Packages registry content.
     */
    private final Content source;

    /**
     * Ctor.
     */
    public JsonPackages() {
        this(
            toContent(
                Json.createObjectBuilder()
                    .add(JsonPackages.ATTRIBUTE, Json.createObjectBuilder())
                    .build()
            )
        );
    }

    /**
     * Ctor.
     *
     * @param source Packages registry content.
     */
    public JsonPackages(final Content source) {
        this.source = source;
    }

    @Override
    public CompletionStage<Packages> add(final Package pack) {
        return new ContentAsJson(this.source)
            .value()
            .thenCompose(
                json -> {
                    if (json.isNull(JsonPackages.ATTRIBUTE)) {
                        throw new IllegalStateException("Bad content, no 'packages' object found");
                    }
                    final JsonObject pkgs = json.getJsonObject(JsonPackages.ATTRIBUTE);
                    return pack.name()
                        .thenApply(Name::string)
                        .thenCompose(
                            pname -> {
                                final JsonObjectBuilder builder;
                                if (pkgs.isEmpty() || pkgs.isNull(pname)) {
                                    builder = Json.createObjectBuilder();
                                } else {
                                    builder = Json.createObjectBuilder(pkgs.getJsonObject(pname));
                                }
                                return pack.version().thenCombine(
                                    pack.json(),
                                    builder::add
                                ).thenApply(
                                    bldr -> new JsonPackages(
                                        toContent(
                                            Json.createObjectBuilder(json)
                                                .add(
                                                    JsonPackages.ATTRIBUTE,
                                                    Json.createObjectBuilder(pkgs).add(pname, bldr)
                                                ).build()
                                        )
                                    )
                                );
                            }
                        );
                }
            );
    }

    @Override
    public CompletionStage<Void> save(final Storage storage, final Key key) {
        return this.content()
            .thenCompose(content -> storage.save(key, content));
    }

    @Override
    public CompletionStage<Content> content() {
        return CompletableFuture.completedFuture(this.source);
    }

    /**
     * Serializes JSON object into content.
     *
     * @param json JSON object.
     * @return Serialized JSON object.
     */
    private static Content toContent(final JsonObject json) {
        return new Content.From(json.toString().getBytes(StandardCharsets.UTF_8));
    }
}
