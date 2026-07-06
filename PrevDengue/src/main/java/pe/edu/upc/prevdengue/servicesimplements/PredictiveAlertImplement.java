package pe.edu.upc.prevdengue.servicesimplements;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pe.edu.upc.prevdengue.entities.*;
import pe.edu.upc.prevdengue.repositories.*;
import pe.edu.upc.prevdengue.servicesinterfaces.IPredictiveAlertService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PredictiveAlertImplement implements IPredictiveAlertService {

    @Autowired
    private IPredictiveAlertRepository paR;
    @Autowired
    private IDistrictRepository districtRepo;
    @Autowired
    private IReportRepository reportRepo;
    @Autowired
    private IRiskLevelRepository riskLevelRepo;
    // @Autowired
    // private IinterventionCampaignRepository campaignRepo; // Opcional para restar riesgo si hay campaña

    @Override
    public List<PredictiveAlert> list() { return paR.findAll(); }

    @Override
    public PredictiveAlert insert(PredictiveAlert pA) { return paR.save(pA); }

    @Override
    public void delete(int idAlert) { paR.deleteById(idAlert); }

    @Override
    public Optional<PredictiveAlert> listId(int idAlert) { return paR.findById(idAlert); }

    @Override
    public void update(PredictiveAlert pA) { paR.save(pA); }

    //@Scheduled(cron = "0 0 0 * * *")
    @Scheduled(cron = "0 * * * * *")// Cada minuto
    public void calcularRiesgoEpidemiologico() {
        System.out.println("⏳ [CRON] Iniciando análisis predictivo de riesgo por distritos...");

        List<District> distritos = districtRepo.findAll();

        for (District distrito : distritos) {
            List<Report> reportesDelDistrito = reportRepo.findByDistrictId(distrito.getIdDistrict());

            long reportesActivos = reportesDelDistrito.stream()
                    .filter(r -> r.getStatus().getIdStatus() == 1 || r.getStatus().getIdStatus() == 2)
                    .count();
            int puntajeRiesgo = (int) (reportesActivos * 10);

            int idRisk = 1; // Por defecto: BAJO
            String mensaje = "Situación controlada. Baja incidencia de reportes.";

            if (puntajeRiesgo >= 50) { // Si hay 5 o más focos activos
                idRisk = 3; // ALTO
                mensaje = "¡ALERTA CRÍTICA! Alta concentración de focos de Dengue activos.";
            } else if (puntajeRiesgo >= 20) {
                idRisk = 2; // MEDIO
                mensaje = "Precaución: Aumento de criaderos reportados en la zona.";
            }

            RiskLevel nivelCalculado = riskLevelRepo.findById(idRisk).orElseThrow();

            List<PredictiveAlert> alertasExistentes = paR.findAll().stream()
                    .filter(a -> a.getDistrict().getIdDistrict() == distrito.getIdDistrict())
                    .toList();

            PredictiveAlert alerta;
            if (!alertasExistentes.isEmpty()) {
                alerta = alertasExistentes.get(0);
            } else {
                alerta = new PredictiveAlert();
                alerta.setDistrict(distrito);
            }

            alerta.setRiskLevel(nivelCalculado);
            alerta.setDescription(mensaje);
            alerta.setAlertDate(LocalDateTime.now());
            alerta.setActive(idRisk > 1);

            paR.save(alerta);
        }

        System.out.println("✅ [CRON] Análisis predictivo completado con éxito.");
    }
}
