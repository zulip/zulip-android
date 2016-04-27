package com.zulip.android;

import java.sql.SQLException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FilterQueryProvider;

import com.j256.ormlite.android.AndroidDatabaseResults;
import com.zulip.android.ZulipAsyncPushTask.AsyncTaskCompleteListener;

public class ComposeDialog extends DialogFragment {
    public static final String COLLATE_NOCASE = " COLLATE NOCASE";
    ZulipActivity activity;
    ZulipApp app;
    private MessageType type;

    private AutoCompleteTextView recipient;
    private AutoCompleteTextView subject;
    private View threadSeparator;
    private EditText body;
    private View view;

    public static ComposeDialog newInstance(MessageType type, String stream,
                                            String topic, String pmRecipients) {
        ComposeDialog d = new ComposeDialog();
        Bundle args = d.getArguments();
        if (args == null) {
            args = new Bundle();
        }
        args.putSerializable("type", type);
        args.putString("stream", stream);
        args.putString("topic", topic);
        args.putString("pmRecipients", pmRecipients);
        d.setArguments(args);
        return d;
    }

    public ComposeDialog() {
    }

    /**
     * Switches the compose window's state to compose a personal message.
     */
    protected void switchToPersonal() {
        subject.setVisibility(View.GONE);
        threadSeparator.setVisibility(View.GONE);
        recipient.setHint(R.string.pm_prompt);
    }

    /**
     * Switches the compose window's state to compose a stream message.
     */
    protected void switchToStream() {
        recipient.setHint(R.string.stream);
    }

    /**
     * Check if a field is nonempty and mark fields as invalid if they are.
     *
     * @param field     The field to check
     * @param fieldName The human-readable name of the field
     * @return Whether the field correctly validated.
     */
    protected boolean requireFilled(EditText field, String fieldName) {
        if (field.getText().toString().equals("")) {
            field.setError("You must specify a " + fieldName);
            field.requestFocus();
            return false;
        }
        return true;

    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setupView(LayoutInflater.from(getActivity()), null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        AlertDialog dialog = builder
                .setView(view)
                .setPositiveButton(R.string.send, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        return dialog;
    }

    private void setupView(LayoutInflater inflater, ViewGroup container) {
        view = inflater.inflate(R.layout.compose, container, false);

        Bundle bundle = getArguments();
        type = (MessageType) bundle.getSerializable("type");
        final String stream = bundle.getString("stream");
        final String topic = bundle.getString("topic");
        final String pmRecipients = bundle.getString("pmRecipients");

        Log.i("onCreateDialog", "" + type + " " + stream + " " + topic + " " + pmRecipients);

        recipient = (AutoCompleteTextView) view.findViewById(R.id.composeRecipient);
        subject = (AutoCompleteTextView) view.findViewById(R.id.composeTopic);
        body = (EditText) view.findViewById(R.id.composeText);
        threadSeparator = view.findViewById(R.id.separator);

        activity = ((ZulipActivity) getActivity());
        app = activity.app;

        if (type == MessageType.STREAM_MESSAGE) {
            this.switchToStream();

            SimpleCursorAdapter recipientAdapter = new SimpleCursorAdapter(
                    getActivity(), R.layout.stream_tile, null,
                    new String[]{Stream.NAME_FIELD},
                    new int[]{R.id.name}, 0);
            recipientAdapter
                    .setCursorToStringConverter(new SimpleCursorAdapter.CursorToStringConverter() {
                        @Override
                        public CharSequence convertToString(Cursor cursor) {
                            int index = cursor
                                    .getColumnIndex(Stream.NAME_FIELD);
                            return cursor.getString(index);
                        }
                    });
            recipient.setAdapter(recipientAdapter);
            recipientAdapter.setFilterQueryProvider(new FilterQueryProvider() {
                @Override
                public Cursor runQuery(CharSequence charSequence) {
                    try {
                        return makeStreamCursor(charSequence);
                    } catch (SQLException e) {
                        ZLog.logException(e);
                        return null;
                    }
                }
            });
            SimpleCursorAdapter subjectAdapter = new SimpleCursorAdapter(
                    getActivity(), R.layout.stream_tile, null,
                    new String[]{Message.SUBJECT_FIELD},
                    new int[]{R.id.name}, 0);
            subjectAdapter.setCursorToStringConverter(new SimpleCursorAdapter.CursorToStringConverter() {
                @Override
                public CharSequence convertToString(Cursor cursor) {
                    int index = cursor
                            .getColumnIndex(Message.SUBJECT_FIELD);
                    return cursor.getString(index);
                }
            });
            subjectAdapter.setFilterQueryProvider(new FilterQueryProvider() {
                @Override
                public Cursor runQuery(CharSequence charSequence) {
                    try {
                        return makeSubjectCursor(recipient.getText(),
                                charSequence);
                    } catch (SQLException e) {
                        ZLog.logException(e);
                        return null;
                    }
                }
            });
            subject.setAdapter(subjectAdapter);
            if (stream != null) {
                recipient.setText(stream);

                if (topic != null) {
                    subject.setText(topic);
                    body.requestFocus();
                } else {
                    subject.setText("");
                    subject.requestFocus();
                }
            } else {
                recipient.setText("");
                recipient.requestFocus();
            }
        } else {
            this.switchToPersonal();
            SimpleCursorAdapter emailAdapter = new SimpleCursorAdapter(
                    getActivity(), R.layout.stream_tile, null,
                    new String[]{Person.EMAIL_FIELD},
                    new int[]{R.id.name}, 0);
            recipient.setAdapter(emailAdapter);
            emailAdapter.setCursorToStringConverter(new SimpleCursorAdapter.CursorToStringConverter() {
                @Override
                public CharSequence convertToString(Cursor cursor) {
                    String text = recipient.getText().toString();
                    String prefix;
                    int lastIndex = text.lastIndexOf(",");
                    if (lastIndex != -1) {
                        prefix = text.substring(0, lastIndex + 1);
                    } else {
                        prefix = "";
                    }
                    int index = cursor
                            .getColumnIndex(Person.EMAIL_FIELD);
                    return prefix + cursor.getString(index);
                }
            });
            emailAdapter.setFilterQueryProvider(new FilterQueryProvider() {
                @Override
                public Cursor runQuery(CharSequence charSequence) {
                    try {
                        return makePeopleCursor(charSequence);
                    } catch (SQLException e) {
                        ZLog.logException(e);
                        return null;
                    }
                }
            });
            if (pmRecipients != null) {
                recipient.setText(pmRecipients);
                body.requestFocus();
            } else {
                recipient.setText("");
                recipient.requestFocus();
            }
        }

    }

    /**
     * Provide streams for autocompletion
     *
     * @param streamName Stream prefix to use for autocompletion
     * @return Cursor with autocompletion results
     * @throws SQLException
     */
    private Cursor makeStreamCursor(CharSequence streamName)
            throws SQLException {
        if (streamName == null) {
            streamName = "";
        }

        // queryRaw is used because of ESCAPE clause
        return ((AndroidDatabaseResults) app
                .getDao(Stream.class)
                .queryRaw(
                        "SELECT rowid _id, * FROM streams WHERE "
                                + Stream.SUBSCRIBED_FIELD + " = 1 AND "
                                + Stream.NAME_FIELD
                                + " LIKE ? ESCAPE '\\' ORDER BY "
                                + Stream.NAME_FIELD + COLLATE_NOCASE,
                        DatabaseHelper.likeEscape(streamName.toString()) + "%")
                .closeableIterator().getRawResults()).getRawCursor();
    }

    /**
     * Provide message subjects for autocompletion
     *
     * @param stream  Only consider messages in this stream. Must be exact match
     * @param subject Subject prefix to use for autocompletion
     * @return Cursor with autocompletion results
     * @throws SQLException
     */
    private Cursor makeSubjectCursor(CharSequence stream, CharSequence subject)
            throws SQLException {
        if (subject == null) {
            subject = "";
        }
        if (stream == null) {
            stream = "";
        }

        // _id must exist to use SimpleCursorAdapter but we can't use rowid
        // because
        // it would interfere with DISTINCT

        // queryRaw is used because of ESCAPE clause
        AndroidDatabaseResults results = (AndroidDatabaseResults) app
                .getDao(Message.class)
                .queryRaw(
                        "SELECT DISTINCT "
                                + Message.SUBJECT_FIELD
                                + ", 1 AS _id FROM messages JOIN streams ON streams."
                                + Stream.ID_FIELD + " = messages."
                                + Message.STREAM_FIELD + " WHERE "
                                + Message.SUBJECT_FIELD
                                + " LIKE ? ESCAPE '\\' AND "
                                + Stream.NAME_FIELD + " = ? ORDER BY "
                                + Message.SUBJECT_FIELD + COLLATE_NOCASE,
                        DatabaseHelper.likeEscape(subject.toString()) + "%",
                        stream.toString()).closeableIterator().getRawResults();
        return results.getRawCursor();
    }

    /**
     * Provide people for autocompletion
     *
     * @param email Prefix to use for matching email addresses
     * @return Cursor with autocompletion results
     * @throws SQLException
     */
    private Cursor makePeopleCursor(CharSequence email) throws SQLException {
        if (email == null) {
            email = "";
        }
        String[] pieces = TextUtils.split(email.toString(), ",");
        String piece;
        if (pieces.length == 0) {
            piece = "";
        } else {
            piece = pieces[pieces.length - 1].trim();
        }
        // queryRaw is used because of ESCAPE clause
        return ((AndroidDatabaseResults) app
                .getDao(Person.class)
                .queryRaw(
                        "SELECT rowid _id, * FROM people WHERE "
                                + Person.ISBOT_FIELD + " = 0 AND "
                                + Person.ISACTIVE_FIELD + " = 1 AND "
                                + Person.EMAIL_FIELD
                                + " LIKE ? ESCAPE '\\' ORDER BY "
                                + Person.NAME_FIELD + COLLATE_NOCASE,
                        DatabaseHelper.likeEscape(piece) + "%")
                .closeableIterator().getRawResults()).getRawCursor();
    }

    private void sending(boolean isSending) {
        view.findViewById(R.id.composeStatus).setVisibility(
                isSending ? View.VISIBLE : View.GONE);

        ((AlertDialog) getDialog()).getButton(DialogInterface.BUTTON_POSITIVE)
                .setEnabled(!isSending);

        recipient.setEnabled(!isSending);
        subject.setEnabled(!isSending);
        body.setEnabled(!isSending);
    }

    @Override
    public void onResume() {
        super.onResume();
        // getButton only works after the view is shown
        ((AlertDialog) getDialog()).getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean valid = true;

                        if (type == MessageType.STREAM_MESSAGE) {
                            valid &= requireFilled(recipient, "recipient"); // stream
                        } else if (type == MessageType.PRIVATE_MESSAGE) {
                            valid &= requireFilled(recipient, "recipient");
                        }

                        valid &= requireFilled(body, "message body");

                        if (valid) {
                            Message msg = new Message(app);
                            msg.setSender(app.you);

                            if (type == MessageType.STREAM_MESSAGE) {
                                msg.setType(MessageType.STREAM_MESSAGE);
                                msg.setStream(new Stream(recipient.getText()
                                        .toString()));
                                msg.setSubject(subject.getText().toString());
                            } else if (type == MessageType.PRIVATE_MESSAGE) {
                                msg.setType(MessageType.PRIVATE_MESSAGE);
                                msg.setRecipient(recipient.getText().toString()
                                        .split(","));
                            }

                            msg.setContent(body.getText().toString());

                            sending(true);

                            AsyncSend sender = new AsyncSend(activity, msg);
                            sender.setCallback(new AsyncTaskCompleteListener() {
                                public void onTaskComplete(String result) {
                                    dismiss();
                                }

                                public void onTaskFailure(String result) {
                                    sending(false);
                                    body.setError("Error sending message");
                                }
                            });

                            sender.execute();
                        }
                    }
                });
    }
}
