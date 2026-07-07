package pe.edu.upc.prevdengue.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.upc.prevdengue.dtos.JwtRequestDTO;
import pe.edu.upc.prevdengue.dtos.JwtResponseDTO;
import pe.edu.upc.prevdengue.securities.JwtTokenUtil;
import pe.edu.upc.prevdengue.servicesimplements.JwtUserDetailsService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import pe.edu.upc.prevdengue.dtos.GoogleTokenDTO;

import java.util.Collections;

@RestController
public class JwtAuthenticationController {
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private JwtUserDetailsService userDetailsService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> login(@RequestBody JwtRequestDTO req) throws Exception {
        authenticate(req.getUsername(), req.getPassword());
        final UserDetails userDetails = userDetailsService.loadUserByUsername(req.getUsername());
        final String token = jwtTokenUtil.generateToken(userDetails);
        return ResponseEntity.ok(new JwtResponseDTO(token));
    }

    private void authenticate(String username, String password) throws Exception {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (DisabledException e) { throw new Exception("USER_DISABLED", e); }
        catch (BadCredentialsException e) { throw new Exception("INVALID_CREDENTIALS", e); }
    }
    @PostMapping("/google")
    public ResponseEntity<?> authenticateWithGoogle(@RequestBody GoogleTokenDTO googleToken) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList("413038221179-6qabb713ebjko62fst9g5348ilsmdab0.apps.googleusercontent.com"))
                    .build();

            GoogleIdToken idToken = verifier.verify(googleToken.getToken());

            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                String email = payload.getEmail();
                System.out.println("Usuario validado por Google: " + email);

                try {
                    // 🚀 1. Buscamos al usuario en tu Base de Datos por su correo (username)
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    // 🚀 2. Generamos el token oficial de PrevDengue
                    final String token = jwtTokenUtil.generateToken(userDetails);

                    // 🚀 3. Lo devolvemos usando tu DTO oficial (igual que en el login normal)
                    return ResponseEntity.ok(new JwtResponseDTO(token));

                } catch (Exception e) {
                    // Si el correo de Google no existe en la BD de PrevDengue, le avisamos
                    java.util.Map<String, String> errorResponse = new java.util.HashMap<>();
                    errorResponse.put("error", "El correo " + email + " no está registrado en el sistema. ¡Regístrate primero!");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
                }

            } else {
                java.util.Map<String, String> errorResponse = new java.util.HashMap<>();
                errorResponse.put("error", "Token de Google inválido.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
        } catch (Exception e) {
            java.util.Map<String, String> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", "Error del servidor: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    }
