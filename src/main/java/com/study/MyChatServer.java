package com.study;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class MyChatServer {

    public static int roomNumber = 1;
    public static Map<Integer, List<String>> chatRooms = new HashMap<>();

    public static void main(String[] args) {
        // ServerSocket 생성
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("서버가 준비되었습니다.");

            Map<String, PrintWriter> chatClients = new HashMap<>();

            // socket 얻어옴
            while (true) {
                Socket socket = serverSocket.accept();
                new ChatThread(socket, chatClients).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

class ChatThread extends Thread {
    private Socket socket;
    private String nickname;  // 클라이언트의 닉네임
    private Map<String, PrintWriter> chatClients;
    private BufferedReader in;
    private PrintWriter out;

    private int currentRoomNumber = -1; // 사용자가 현재 위치해있는 방 번호. -1은 로비

    public ChatThread(Socket socket, Map<String, PrintWriter> chatClients) {
        this.socket = socket;
        this.chatClients = chatClients;

        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            nickname = in.readLine();   // 클라이언트가 접속하자마자 첫 줄로 닉네임을 보냄

            synchronized (chatClients) {
                while (chatClients.containsKey(nickname)) { // 중복 닉네임 확인
                    out.println("이미 사용 중인 닉네임입니다. 사용할 다른 닉네임을 입력하세요");
                    nickname = in.readLine();
                }
                // 유저 목록 HashMap에 넣기. 키 = 닉네임 / 값 = 해당 클라이언트의 PrintWriter 객체
                chatClients.put(this.nickname, out);
            }

            System.out.println(nickname + " 닉네임의 사용자가 연결했습니다.");
            System.out.println(nickname + " 사용자의 IP 주소는 " + socket.getInetAddress().getHostAddress() + " 입니다.");

            sendMenu();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        String msg = null;

        try {
            while ((msg = in.readLine()) != null) {
                if ("/bye".equalsIgnoreCase(msg)) {
                    break;
                } else if ("/create".equalsIgnoreCase(msg)) {
                    createChatRoom();
                } else if (msg.indexOf("/join") == 0) {
                    joinChatRoom(msg);
                } else if ("/list".equalsIgnoreCase(msg)) {
                    findChatRoom();
                } else if ("/exit".equalsIgnoreCase(msg)) {
                    exitChatRoom();
                } else if ("/users".equalsIgnoreCase(msg)) {
                    findAllUsers();
                } else if ("/roomusers".equalsIgnoreCase(msg)) {
                    findRoomUsers();
                } else if (msg.indexOf("/whisper") == 0) {
                    whisperToUser(msg);
                } else if (currentRoomNumber != -1)
                    broadcastRoomUsers(msg);

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            synchronized (chatClients) {
                // 사용자가 나가면 삭제
                System.out.println(nickname + " 닉네임의 사용자가 연결을 끊었습니다.");
                chatClients.remove(nickname);
            }
        }

        if (out != null) {
            out.close();
        }

        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void sendMenu() {
        String[] menus = {
                "방 목록 보기 : /list",
                "방 생성 : /create",
                "방 입장 : /join",
                "방 나가기 : /exit",
                "전체 유저 목록 보기 : /users",
                "현재 채팅방 유저 목록 보기 : /roomusers",
                "귓속말 : /whisper [닉네임] [메시지]",
                "접속종료 : /bye"
        };

        for (String menu : menus) {
            out.println(menu);
        }
    }

    private void createChatRoom() {
        if (currentRoomNumber != -1)    // 다른 방에 참가한 적이 있다면 전에 있던 방 먼저 나가기 처리
            exitChatRoom();

        List<String> roomUsers = new ArrayList<>();

        roomUsers.add(nickname);
        currentRoomNumber = MyChatServer.roomNumber;

        out.println("방 번호 [" + MyChatServer.roomNumber + "]가 생성되었습니다.");
        MyChatServer.chatRooms.put(MyChatServer.roomNumber, roomUsers);
        MyChatServer.roomNumber++;
    }

    private void joinChatRoom(String msg) {
        int firstSpaceIndex = msg.indexOf(" "); // 첫 번째 공백의 인덱스
        if (firstSpaceIndex == -1) return;  // 첫번째 공백(채팅방 번호)이 없다면 메서드 실행 X

        int roomNumber = Integer.parseInt(msg.substring(firstSpaceIndex + 1));

        if (!MyChatServer.chatRooms.containsKey(roomNumber)) return; // 존재하는 채팅방이 아니면 메서드 실행 X
        if (currentRoomNumber == roomNumber) return;    // 이미 내가 들어와있는 방이면 메서드 실행 X
        if (currentRoomNumber != -1)    // 다른 방에 참가한 적이 있다면 전에 있던 방 먼저 나가기 처리
            exitChatRoom();

        currentRoomNumber = roomNumber;

        List<String> currentRoomUser = MyChatServer.chatRooms.get(roomNumber);
        currentRoomUser.add(nickname);
        MyChatServer.chatRooms.put(roomNumber, currentRoomUser);

        broadcastRoomUsers(nickname + "이 방에 입장했습니다.");
    }

    private void findChatRoom() {
        Iterator it = MyChatServer.chatRooms.keySet().iterator();

        if (!it.hasNext()) {
            out.println("존재하는 채팅방이 없습니다.");
        } else {
            while (it.hasNext()) {
                int key = (int) it.next();
                out.println(key);
            }
        }
    }

    private void exitChatRoom() {
        if (currentRoomNumber == -1)    // 이미 로비에 있는 상태면 메서드 실행 X
            return;

        Iterator<Map.Entry<Integer, List<String>>> it = MyChatServer.chatRooms.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<String>> e = it.next();
            int key = e.getKey();
            List<String> users = e.getValue();

            if (users.contains(nickname)) {
                MyChatServer.chatRooms.get(key).remove(nickname);
                broadcastRoomUsers(nickname + "님이 방을 나갔습니다.");

                // 방에 아무도 없으면 방 삭제
                if (MyChatServer.chatRooms.get(key).isEmpty())
                    it.remove();

                currentRoomNumber = -1; // 방 나간 상태니까 현재 사용자가 있는 방 번호 -1로 바꿔줌
            }
        }

        sendMenu(); // 메인 메뉴 호출

    }

    private void findAllUsers() {
        Iterator<String> allUsers = chatClients.keySet().iterator();
        while (allUsers.hasNext()) {
            String user = allUsers.next();
            out.println(user);
        }
    }

    private void findRoomUsers() {
        if (currentRoomNumber == -1) {
            out.println("채팅방에 접속 중이 아닙니다.");
            return;
        }

        Iterator<String> roomUsers = MyChatServer.chatRooms.get(currentRoomNumber).iterator();
        while (roomUsers.hasNext()) {
            String user = roomUsers.next();
            out.println(user);
        }
    }

    public void broadcastRoomUsers(String msg) {
        synchronized (chatClients) {
            List<String> roomUsers = MyChatServer.chatRooms.get(currentRoomNumber);
            for (String user : roomUsers) {
                PrintWriter out = chatClients.get(user);    // (key)에 해당하는 value 값 가져오기
                if (out != null)
                    out.println(nickname + ": " + msg);
            }
        }
    }

    public void whisperToUser(String msg) {
        // 귓속말을 /whisper [닉네임] [메시지] 로 보냄
        int firstSpaceIndex = msg.indexOf(" "); // 첫 번째 공백의 인덱스
        if (firstSpaceIndex == -1) return;  // 첫번째 공백(수신자)이 없다면 메서드 실행 X

        int secondSpaceIndex = msg.indexOf(" ", firstSpaceIndex + 1);   // 두 번째 공백의 인덱스
        if (secondSpaceIndex == -1) return; // 두번째 공백 (수신자는 있고 보낼 메시지)가 없다면 메서드 실행 X

        String to = msg.substring(firstSpaceIndex + 1, secondSpaceIndex);   // 수신자 얻기
        String message = msg.substring(secondSpaceIndex + 1);

        if (chatClients.containsKey(to)) {
            PrintWriter out = chatClients.get(to);
            if (out != null)
                out.println(nickname + "님으로부터 온 귓속말 : " + message);
        } else {
            out.println(to + "님을 찾을 수 없습니다.");
        }


    }


}
