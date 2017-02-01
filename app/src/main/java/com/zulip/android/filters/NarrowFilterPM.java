package com.zulip.android.filters;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.Where;
import com.zulip.android.ZulipApp;
import com.zulip.android.models.Message;
import com.zulip.android.models.MessageType;
import com.zulip.android.models.Person;
import com.zulip.android.models.Stream;

import org.json.JSONArray;
import org.json.JSONException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NarrowFilterPM implements NarrowFilter {
    public static final Parcelable.Creator<NarrowFilterPM> CREATOR = new Parcelable.Creator<NarrowFilterPM>() {
        public NarrowFilterPM createFromParcel(Parcel in) {
            return new NarrowFilterPM(in.readString());
        }

        public NarrowFilterPM[] newArray(int size) {
            return new NarrowFilterPM[size];
        }
    };
    private List<Person> people;
    private String recipientString;

    public NarrowFilterPM(List<Person> people) {
        this.people = people;
        this.recipientString = Message.recipientList(people
                .toArray(new Person[people.size()]));
    }

    private NarrowFilterPM(String recipientString) {
        this.recipientString = recipientString;
        this.people = new ArrayList<>();
        for (String id : this.recipientString.split(",")) {
            this.people
                    .add(Person.getById(ZulipApp.get(), Integer.valueOf(id)));
        }
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.recipientString);
    }

    @Override
    public Where<Message, Object> modWhere(Where<Message, Object> where)
            throws SQLException {

        where.eq(Message.RECIPIENTS_FIELD, new SelectArg(recipientString));
        return where;
    }

    @Override
    public boolean matches(Message msg) {
        return msg.getType() == MessageType.PRIVATE_MESSAGE;
    }

    @Override
    public String getTitle() {
        ArrayList<String> names = new ArrayList<>();
        for (Person person : people) {
            // If PM to self then show title as your name
            // people size == 1 implies PM to self
            if (person.getId() != ZulipApp.get().getYou().getId() || people.size() == 1) {
                names.add(person.getName());
            }
        }
        return TextUtils.join(", ", names);
    }

    @Override
    public String getSubtitle() {
        return null;
    }

    @Override
    public Stream getComposeStream() {
        return null;
    }

    @Override
    public String getComposePMRecipient() {
        return Message.emailsMinusYou(people, ZulipApp.get().getYou());
    }

    @Override
    public String getJsonFilter() throws JSONException {
        ArrayList<String> emails = new ArrayList<>();
        for (Person person : this.people) {
            if (!person.equals(ZulipApp.get().getYou())) {
                emails.add(person.getEmail());
            }
        }
        return (new JSONArray()).put(
                new JSONArray(Arrays.asList("pm-with",
                        TextUtils.join(",", emails)))).toString();
    }

    @Override
    public boolean equals(NarrowFilter filter) {
        if (filter instanceof NarrowFilterPM) {
            NarrowFilterPM filterPM = (NarrowFilterPM) filter;
            return this.getTitle().equals(filterPM.getTitle());
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        try {
            return getJsonFilter();
        } catch (JSONException e) {
            return null;
        }
    }
}
