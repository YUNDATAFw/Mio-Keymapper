package io.takamiya.mio.utils;

import android.content.Context;

import com.bumptech.glide.Glide;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.image.glide.GlideImagesPlugin;

public final class MarkdownProvider {

    private MarkdownProvider() {
    }

    public static Markwon create(Context context) {
        return Markwon.builder(context)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(GlideImagesPlugin.create(Glide.with(context.getApplicationContext())))
                .build();
    }
}
