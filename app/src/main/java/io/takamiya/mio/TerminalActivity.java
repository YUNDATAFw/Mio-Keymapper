package io.takamiya.mio;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import io.noties.markwon.Markwon;
import io.takamiya.mio.adapter.TerminalResultAdapter;
import io.takamiya.mio.databinding.ActivityTerminalBinding;
import io.takamiya.mio.utils.MarkdownProvider;
import io.takamiya.mio.terminal.command.CommandCallback;
import io.takamiya.mio.terminal.command.CommandDispatcher;
import io.takamiya.mio.terminal.command.CommandRegistry;
import io.takamiya.mio.terminal.command.CommandResult;
import io.takamiya.mio.terminal.command.CommandState;
import io.takamiya.mio.terminal.commands.AllFilesCommand;
import io.takamiya.mio.terminal.commands.HelpCommand;
import io.takamiya.mio.terminal.commands.PluginUtilsCommand;
import io.takamiya.mio.terminal.commands.RegCommand;

public class TerminalActivity extends AppCompatActivity {

    private ActivityTerminalBinding binding;
    private CommandDispatcher dispatcher;
    private TerminalResultAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTerminalBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        applyWindowInsets(binding);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        CommandRegistry registry = new CommandRegistry();
        registry.register(new HelpCommand(registry));
        registry.register(new RegCommand());
        registry.register(new PluginUtilsCommand());
        registry.register(new AllFilesCommand(this));
        dispatcher = new CommandDispatcher(registry);

        Markwon markwon = MarkdownProvider.create(this);
        adapter = new TerminalResultAdapter(markwon);
        binding.rvResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvResults.setAdapter(adapter);

        binding.btnExecute.setOnClickListener(v -> executeCommand());

        binding.etCommand.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                executeCommand();
                return true;
            }
            return false;
        });

        binding.btnClear.setOnClickListener(v -> adapter.clear());
    }

    private void executeCommand() {
        String input = binding.etCommand.getText() != null
                ? binding.etCommand.getText().toString().trim() : "";
        if (input.isEmpty()) return;

        binding.etCommand.setText("");
        adapter.addResult(input, CommandState.PARSING, null, null, null, 0);
        binding.rvResults.scrollToPosition(adapter.getItemCount() - 1);
        binding.tvGlobalState.setText("执行中...");

        dispatcher.dispatch(input, new CommandCallback() {
            @Override
            public void onStateChange(CommandState state) {
                runOnUiThread(() -> {
                    adapter.updateLastState(state);
                    binding.rvResults.scrollToPosition(adapter.getItemCount() - 1);
                    if (state == CommandState.SUCCESS || state == CommandState.ERROR) {
                        binding.tvGlobalState.setText("空闲");
                    }
                });
            }

            @Override
            public void onResult(CommandResult result) {
                runOnUiThread(() -> {
                    String output = result.getOutput();
                    String markdown = result.getMarkdown();
                    String error = result.getError();
                    adapter.updateLastResult(output, markdown, error, result.getDurationMs());
                    binding.rvResults.scrollToPosition(adapter.getItemCount() - 1);
                });
            }
        });
    }

    private void applyWindowInsets(ActivityTerminalBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    bars.top,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            binding.content.setPadding(
                    binding.content.getPaddingLeft(),
                    binding.content.getPaddingTop(),
                    binding.content.getPaddingRight(),
                    bars.bottom);
            return insets;
        });
    }
}
