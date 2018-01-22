package io.condividifacile.activities;

import android.os.Bundle;
import android.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.condividifacile.data.Notification;
import io.condividifacile.R;
import io.condividifacile.data.Expense;

public class AddExpenseActivity extends AppCompatActivity {

    private String name;
    private ArrayList <Pair<String,String>> members;
    private ArrayList<Pair<String,Double>> userBalance;
    private FirebaseDatabase database;
    private DatabaseReference expenseRef;
    private LinearLayout boxesLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        database = FirebaseDatabase.getInstance();
        final String selectedGroup = getIntent().getExtras().getString("selectedGroup");
        TextView title = (TextView) findViewById(R.id.addExpTitle);
        title.setText("Add an expense to "+selectedGroup+":");
        //Getting group members, categories
        members = new ArrayList<>();
        boxesLayout = (LinearLayout) findViewById(R.id.boxes_layout);
        final ArrayList<String> categories = new ArrayList<>();
        final AutoCompleteTextView categoryEdit = (AutoCompleteTextView) findViewById(R.id.categoryEdit);
        final ArrayAdapter<String> categoryAdapter = new ArrayAdapter<String>(AddExpenseActivity.this,android.R.layout.simple_spinner_dropdown_item,categories);
        categoryEdit.setThreshold(0);
        categoryEdit.setAdapter(categoryAdapter);

        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            name = user.getDisplayName();
            DatabaseReference groupsRef = database.getReference("groups");
            groupsRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    boxesLayout.removeAllViews();
                    members.clear();
                    for(DataSnapshot singleSnapshot : dataSnapshot.getChildren()){
                        if(singleSnapshot.child("name").getValue().equals(selectedGroup)) {
                            Log.d("swag","checking group "+singleSnapshot.child("name").getValue());
                            expenseRef = singleSnapshot.child("expenses").getRef();
                            for(DataSnapshot membersSnap : singleSnapshot.child("members").getChildren()){
                                String member = membersSnap.getKey();
                                String id = (String) membersSnap.getValue();
                                members.add(new Pair<String, String>(member, id));
                                if(!member.equals(user.getDisplayName())) {
                                    addMemberBox(member);
                                }
                            }

                            for(DataSnapshot categoriesSnap : singleSnapshot.child("categories").getChildren()){
                                String category = categoriesSnap.getKey();
                                categories.add(category);
                                categoryAdapter.notifyDataSetChanged();
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }

        //Getting user balance
        getUserBalance(user.getUid(),selectedGroup);
        final EditText amountText = (EditText) findViewById(R.id.amountEdit);

        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy", Locale.ITALY);
        final String formattedDate = df.format(c.getTime());

        Button completeBtn = (Button) findViewById(R.id.completeExp);
        completeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                float amount = Float.parseFloat(amountText.getText().toString());
                String category = categoryEdit.getText().toString();
                Expense exp = new Expense();
                exp.setCategory(category);
                if (!categories.contains(category)){
                    expenseRef.getParent().child("categories").child(category).setValue(true);
                }
                exp.setBuyer(name);
                exp.setDate(formattedDate);
                exp.setAmount(amount);

                LinearLayout boxesLayout = (LinearLayout) findViewById(R.id.boxes_layout);
                int n_boxes = boxesLayout.getChildCount();
                final ArrayList <String> selectedMembers = new ArrayList<String>();
                selectedMembers.add(user.getDisplayName());
                for(int i = 0 ; i < n_boxes; i++) {
                    CheckBox memberBox = (CheckBox) boxesLayout.getChildAt(i);
                    if(memberBox.isChecked()){
                        selectedMembers.add(memberBox.getText().toString());
                    }
                }

                final HashMap<String,Double> divisions = divideEqually(amount, selectedMembers);

                exp.setDivision(divisions);
                expenseRef.push().setValue(exp);
                for(int i = 0; i < members.size(); i++){
                    String notificText = "From "+user.getDisplayName()+" in "+selectedGroup;
                    if(!members.get(i).first.equals(user.getDisplayName())) {
                        sendNotification(members.get(i).second, notificText, "Expense added", "notification");
                    }
                }
                final ArrayList<Pair<String,Double>> newUserBalance = new ArrayList<Pair<String, Double>>();
                for(int i = 0; i < userBalance.size(); i++){
                    for(int j = 0; j < divisions.size(); j++){
                        if(divisions.get(userBalance.get(i).first) != null){
                            double oldBalance = userBalance.get(i).second;
                            double newBalance = Math.round((oldBalance + divisions.get(userBalance.get(i).first))*100.0)/100.0;
                            newUserBalance.add(new Pair<String, Double>(userBalance.get(i).first,newBalance));
                        }
                    }
                }
                DatabaseReference usersRef = database.getReference("users");

                for(int i = 0; i < members.size(); i++){

                    //Updating balance for the logged user
                    if(members.get(i).first.equals(user.getDisplayName())){
                        for(int j = 0; j < newUserBalance.size(); j++) {
                            if (!members.get(i).first.equals(newUserBalance.get(j).first)) {
                                usersRef.child(members.get(i).second).child("groups").child(selectedGroup).child(newUserBalance.get(j).first).setValue(newUserBalance.get(j).second);
                            }
                        }
                    }//else update balance for other users in the group
                    else{
                        double invertedBalance = 0;
                        for(int j = 0; j < newUserBalance.size(); j++){
                            if(newUserBalance.get(j).first.equals(members.get(i).first)){
                                invertedBalance = -newUserBalance.get(j).second;
                            }
                        }
                        usersRef.child(members.get(i).second).child("groups").child(selectedGroup).child(user.getDisplayName()).setValue(invertedBalance);
                    }
                }


                finish();
            }
        });

    }

    private void addMemberBox(String member){
        CheckBox box = new CheckBox(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0,10,0,0);
        box.setLayoutParams(params);
        box.setId(members.indexOf(member));
        box.setText(member);
        box.setChecked(true);
        boxesLayout.addView(box);
    }

    // returns an ArrayList<Double> with the total divided in equal amounts as by the divider parameter
    public HashMap<String,Double> divideEqually(double total, ArrayList<String> members)
    {
        HashMap<String,Double> membersBalance = new HashMap<>();
        int divider = members.size();
        for(int i = 0; i < members.size(); i++) {
            double amount = Math.round((total / divider) * 100.0) / 100.0;
            membersBalance.put(members.get(i),amount);
            total -= amount;
            divider--;
        }

        return membersBalance;
    }

    //method to get user balance inside a group from DB
    private void getUserBalance(String userId, String groupName){

        userBalance = new ArrayList<>();
        DatabaseReference balanceRef = database.getReference("users/"+userId+"/groups/"+groupName);
        balanceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                for(DataSnapshot singleSnapshot : dataSnapshot.getChildren()){
                    String member = singleSnapshot.getKey();
                    double balance = Double.parseDouble(singleSnapshot.getValue().toString());
                    android.util.Pair<String, Double> memberBalance = new android.util.Pair<String, Double>(member,balance);
                    userBalance.add(memberBalance);
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public static void sendNotification(String user_id,String message,String description,String type){
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("notifications").child(user_id);
        String pushKey = databaseReference.push().getKey();

        Notification notification = new Notification();
        notification.setDescription(description);
        notification.setMessage(message);
        notification.setUser_id(user_id);
        notification.setType(type);

        Map<String, Object> forumValues = notification.toMap();
        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put(pushKey, forumValues);
        databaseReference.setPriority(ServerValue.TIMESTAMP);
        databaseReference.updateChildren(childUpdates, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if(databaseError == null){

                }
            }
        });
    }
}
