package edu.uclm.esi.tysweb.websocket;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

import javax.servlet.http.HttpSession;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.uclm.esi.tysweb.games.GamesManager;
import edu.uclm.esi.tysweb.games.Match;
import edu.uclm.esi.tysweb.games.Player;

@ServerEndpoint(value = "/ws_games", configurator = HttpSessionConfigurator.class)
public class WsServerGames {

	static Map<String, Player> players = new HashMap<String, Player>();
	static Map<String, Session> connections = new HashMap<String, Session>();

	@OnOpen
	public void onOpen(Session session, EndpointConfig config) {
		HttpSession httpSession = (HttpSession) config.getUserProperties().get(HttpSession.class.getName());
		httpSession.setAttribute("wsGameSession", session);
		Player player = (Player) httpSession.getAttribute("player");
		System.out.println("OPEN WS GAME"+ player.getUserName());
		players.put(session.getId(), player);
		connections.put(session.getId(), session);
	}

	@OnMessage
	public void onMessage(String message, Session session) {
		try {
			JSONObject jso = new JSONObject(message);
			if(jso.get("TYPE").equals("BEGIN_MATCH")) {
				Player player = players.get(session.getId());
				Match match = GamesManager.get().joinGame(player, jso.get("GAME").toString());
				if (match.getPlayers().size()==2) {
					send(match.getPlayers(), match);
				}else {
					String s= "Waiting opponent...";	
					send(s, player, session);
				}
				
			}else if (jso.get("TYPE").equals("MOVEMENT")) {
				Player player = players.get(session.getId());
				JSONArray array = jso.getJSONArray("MOVE");
				int[] coordinates = new int[array.length()];
				for(int i = 0; i<array.length(); i++) {
					coordinates[i] = array.getInt(i);
				}
				try {
					Match match = player.getCurrentMatch().move(player, coordinates);
					send(match.getPlayers(), match);
				}catch(Exception e) {
					System.out.println(player.getUserName()+" "+e.getMessage());
				}
				
			}else if(jso.get("TYPE").equals("QUIT")) {
				Player player = players.get(session.getId());
				Match match = player.quitgame();
				players.remove(session.getId());
				connections.remove(session.getId());
				send(match.getPlayers(), match);
				
			}else if(jso.get("TYPE").equals("START")) {
				Player player = players.get(session.getId());
				Match match = player.getCurrentMatch();
				if(match.getCurrentPlayer() == -1) {
					match.calculateFirstPlayer();
					send(match.getPlayers(), match);					
				}
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
			players.remove(session.getId());
			connections.remove(session.getId());
		}
	}

	@OnClose
	public void onClose(Session session) {
		System.out.println("CLOSE WS GAME");
		players.remove(session.getId());
		connections.remove(session.getId());
	}

	public static void send(Vector<Player> playersToSend, Match match) {
		ObjectMapper mapper = new ObjectMapper();
		JSONObject jso;
		try {
			jso = new JSONObject(mapper.writeValueAsString(match));
			jso.put("TYPE", "MATCH");
			for(Player player : playersToSend) {
				Session s = connections.get(getSessionIdByPlayer(player));
				s.getBasicRemote().sendText(jso.toString());
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

	private static String getSessionIdByPlayer(Player player) {
		String s_id = null;
		Set<Entry<String, Player>> set = players.entrySet();
		Iterator<Entry<String, Player>> it = set.iterator();
		while(it.hasNext()) {
			Entry<String, Player> phase = it.next();
			if(phase.getValue().equals(player)) 
				s_id = phase.getKey();
		}
		return s_id;
	}
	public static void send(String s, Player p, Session session) {
		JSONObject jso = new JSONObject();
		try {
			jso.put("text", s);
			jso.put("player", p);
			jso.put("TYPE", "INFORMATIVE");
			session.getBasicRemote().sendText(jso.toString());
		}catch(Exception e) {
			
		}
	}

}
